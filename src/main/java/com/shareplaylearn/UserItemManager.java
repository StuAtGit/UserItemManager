package com.shareplaylearn;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.Base64;
import com.shareplaylearn.exceptions.InternalErrorException;
import com.shareplaylearn.models.ItemSchema;
import com.shareplaylearn.models.UploadMetadataFields;
import com.shareplaylearn.models.UserItem;
import com.shareplaylearn.services.ImagePreprocessorPlugin;
import com.shareplaylearn.services.SecretsService;
import com.shareplaylearn.services.UploadPreprocessor;
import com.shareplaylearn.services.UploadPreprocessorPlugin;
import com.shareplaylearn.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.*;
import java.util.*;

/**
 * Created by stu on 9/6/15.
 * Metadata about the items the user has, and possibly
 * cached values for the items.
 * Location, type, names, etc.
 * Data here should be safe to cache in Redis (userid but no auth tokens, etc).
 * Right now, we just implicitly store the metadata as part of the item path.
 * Once we have a true metadata store, this code can be greatly simplified.
 * (in particular, the getItemList() that reconstructs metadata from the path).
 */
public class UserItemManager {
    public static class AvailableEncodings {
        public static final String BASE64 = "BASE64";
        public static final String IDENTITY = "IDENTITY";
        public static boolean isAvailable( String encoding ) {
            return encoding.equals(BASE64) || encoding.equals(IDENTITY);
        }
    }
    private static final String ROOT_DIR = "/root/";

    private int totalItemQuota = Limits.DEFAULT_ITEM_QUOTA;
    private HashMap<String,Integer> itemQuota;
    private Logger log;

    private String userName;
    private String userId;
    private String userDir;

    public UserItemManager(String userName, String userId) {
        this.userName = userName;
        this.userId = userId;
        this.userDir = this.getUserDir();
        this.itemQuota = new HashMap<>();
        this.itemQuota.put(ItemSchema.IMAGE_CONTENT_TYPE, Limits.DEFAULT_ITEM_QUOTA);
        this.itemQuota.put(ItemSchema.UNKNOWN_CONTENT_TYPE, Limits.DEFAULT_ITEM_QUOTA / 2);
        this.log = LoggerFactory.getLogger(UserItemManager.class);
    }

    public Response addItem( String name, byte[] item ) throws InternalErrorException {
        Response quotaCheck = this.checkQuota();
        if( quotaCheck.getStatus() != 200 ) {
            return quotaCheck;
        }

        List<UploadPreprocessorPlugin> uploadPreprocessorPlugins = new ArrayList<>();
        uploadPreprocessorPlugins.add(new ImagePreprocessorPlugin());
        UploadPreprocessor uploadPreprocessor = new UploadPreprocessor( uploadPreprocessorPlugins );
        Map<ItemSchema.PresentationType,byte[]> uploads = uploadPreprocessor.process(item);

        if( uploads.size() == 0 ) {
            throw new InternalErrorException("Upload processor returned empty upload set");
        }

        UploadPreprocessorPlugin pluginUsed = uploadPreprocessor.getProcessorPluginUsed();
        String contentType = pluginUsed.getContentType();

        for( Map.Entry<ItemSchema.PresentationType,byte[]> uploadEntry : uploads.entrySet() ) {
            boolean found = false;
            ItemSchema.PresentationType presentationType = uploadEntry.getKey();
            for( ItemSchema.PresentationType type : ItemSchema.PRESENTATION_TYPES) {
                if( presentationType.equals(type) ) {
                    found = true;
                }
            }
            if( found ) {
                //this is a little bit of a hack, but is necessary for downloads
                //using the name of the item to work
                //OK in user agents (browsers)
                if( presentationType.equals(ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE) &&
                        pluginUsed.getPreferredFileExtension() != null &&
                        pluginUsed.getPreferredFileExtension().length() > 0 &&
                        !name.endsWith(pluginUsed.getPreferredFileExtension() ) ) {
                    String preferredExtension = pluginUsed.getPreferredFileExtension();
                    if( !preferredExtension.startsWith(".") ) {
                        preferredExtension = "." + preferredExtension;
                    }
                    int extIndex = name.lastIndexOf(".");
                    if( extIndex > 0 ) {
                        this.saveItem(name.substring(0, extIndex) + preferredExtension,
                                uploadEntry.getValue(), contentType, presentationType);
                    } else {
                        this.saveItem(name + preferredExtension, uploadEntry.getValue(), contentType, presentationType);
                    }
                } else {
                    this.saveItem(name, uploadEntry.getValue(), contentType, presentationType);
                }
            } else {
                log.error( "Upload plugin had an entry with a presentation type of: " + presentationType
                        + " that was not found in the item types defined in the ItemSchema.");
            }
        }

        return Response.status(200).build();
    }

    public Response getItem(String contentType, ItemSchema.PresentationType presentationType,
                            String name, String encoding ) {
        if( encoding != null && encoding.length() > 0 && !AvailableEncodings.isAvailable(encoding) ) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .entity("Inner Encoding Type: " + encoding + " not available").build();
        }

        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret)
        );

        try
        {
            S3Object object = s3Client.getObject(
                    ItemSchema.S3_BUCKET,
                    getItemLocation(name, contentType, presentationType)
            );
            try( S3ObjectInputStream inputStream = object.getObjectContent() ) {
                long contentLength = object.getObjectMetadata().getContentLength();
                if (contentLength > Limits.MAX_RETRIEVE_SIZE) {
                    throw new IOException("Object is to large: " + contentLength + " bytes.");
                }
                int bufferSize = Math.min((int) contentLength, 10 * 8192);
                byte[] buffer = new byte[bufferSize];
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                int bytesRead = 0;
                int totalBytesRead = 0;
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                log.debug("GET in file resource read: " + totalBytesRead + " bytes.");
                if( encoding == null || encoding.length() == 0 || encoding.equals(AvailableEncodings.IDENTITY) ) {
                    return Response.status(Response.Status.OK).entity(outputStream.toByteArray()).build();
                } else if( encoding.equals(AvailableEncodings.BASE64) ) {
                    return Response.status(Response.Status.OK)
                            .entity(Base64.encodeAsString(outputStream.toByteArray())).build();
                } else {
                    return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                            .entity("Inner Encoding Type: " + encoding + " not available").build();
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("\nFailed to retrieve: " + name);
            e.printStackTrace(pw);
            log.warn("Failed to retrieve: " + name);
            log.info(Exceptions.asString(e));
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(sw.toString()).build();
        }
    }

    /**
     * Writes items to S3, and item metadata to Redis
     */
    private void saveItem( String name, byte[] itemData, String contentType, ItemSchema.PresentationType presentationType  )
            throws InternalErrorException {

        String itemLocation = this.getItemLocation(name, contentType, presentationType);
        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret)
        );
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(itemData);
        ObjectMetadata metadata = this.makeBasicMetadata(itemData.length, false, name);
        metadata.addUserMetadata(UploadMetadataFields.CONTENT_TYPE, contentType);
        //TODO: save this metadata, along with location, to local Redis
        s3Client.putObject(ItemSchema.S3_BUCKET, itemLocation, byteArrayInputStream, metadata);
    }

    private ObjectMetadata makeBasicMetadata( int bufferLength, boolean isPublic, String itemName ) {
        ObjectMetadata fileMetadata = new ObjectMetadata();
        fileMetadata.setContentEncoding(MediaType.APPLICATION_OCTET_STREAM);
        if (isPublic) {
            fileMetadata.addUserMetadata(UploadMetadataFields.PUBLIC, UploadMetadataFields.TRUE_VALUE);
        } else {
            fileMetadata.addUserMetadata(UploadMetadataFields.PUBLIC, UploadMetadataFields.FALSE_VALUE);
        }
        fileMetadata.addUserMetadata(UploadMetadataFields.DISPLAY_NAME, itemName);
        fileMetadata.setContentLength(bufferLength);
        return fileMetadata;
    }

    /**
     * @return
     */
    public List<UserItem> getItemList() {
        HashMap<String,HashMap<ItemSchema.PresentationType,List<String>>> itemLocations = getItemLocations();
        List<UserItem> itemList = new ArrayList<>();

        //on a per-user basis, then name of the item should be unique,
        //but variations of it may be stored under different presentation types
        //(e.g. preview, original)
        //there is a workaround here because we may add a file extension to some preferred items
        //we work around it by verifying a preferred item doesn't have an entry that matches without extension
        //a better fix would be a metadata store fast enough to be usable that stores the original item name
        //but this works for now.
        HashMap<String,UserItem> userItems = new HashMap<>();

        for( Map.Entry<String, HashMap<ItemSchema.PresentationType, List<String>>> items : itemLocations.entrySet() ) {
            String contentType = items.getKey();

            for( Map.Entry<ItemSchema.PresentationType, List<String>> item : items.getValue().entrySet() ) {
                ItemSchema.PresentationType presentationType = item.getKey();

                List<String> locations = item.getValue();
                for( String location : locations ) {
                    String[] path = location.split("/");
                    if (path.length == 0) {
                        log.warn("Found an item path/location with no directory structure: " + location);
                        continue;
                    }
                    String name = path[path.length - 1];
                    //workaround (see above)
                    int extIndex = name.lastIndexOf(".");
                    if( extIndex > 0 ) {
                        name = name.substring(0, extIndex);
                    }
                    log.debug("Got a location: " + location + " for item with name: " + name + " for user: " + this.userName);
                    UserItem userItem = null;
                    if (!userItems.containsKey(name)) {
                        userItems.put(name, new UserItem(contentType));
                    }
                    userItem = userItems.get(name);
                    userItem.setLocation(presentationType, location);
                    if (presentationType.equals(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE)) {
                        userItem.addAttr("altText", "Preview of " + name);
                    }
                }
            }
        }
        for( Map.Entry<String, UserItem> userItem : userItems.entrySet() ) {
            //Note that this maps to the actual name in all cases (original, preview, preferred w/out added extension)
            //except when we add an extension to a preferred format of the item.
            userItem.getValue().addAttr(UploadMetadataFields.DISPLAY_NAME, userItem.getKey());
            itemList.add(userItem.getValue());
        }
        return itemList;
    }

    public HashMap<String,HashMap<ItemSchema.PresentationType,List<String>>> getItemLocations() {
        HashMap<String,HashMap<ItemSchema.PresentationType,List<String>>> itemLocations = new HashMap<>();

        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret));

        for( String contentType : ItemSchema.CONTENT_TYPES ) {
            for( ItemSchema.PresentationType presentationType : ItemSchema.PRESENTATION_TYPES ) {

                ObjectListing listing = s3Client.listObjects( ItemSchema.S3_BUCKET,
                        this.getItemDirectory(contentType, presentationType) );

                HashSet<String> locations = getExternalItemListing(listing);
                String curDirectory = makeExternalLocation(getItemDirectory(contentType, presentationType));
                for( String location : locations ) {
                    //it would be nice if s3 didn't return stuff that doesn't technically match the prefix
                    //(due to trailing /), but it looks like it might
                    if( curDirectory.endsWith(location) ) {
                        log.debug( "Skipping location: " + location + " because it looks like a group (folder)" +
                                ", not an object" );
                        continue;
                    }
                    if( !itemLocations.containsKey(contentType) ) {
                        itemLocations.put(contentType, new HashMap<>() );
                    }
                    if( !itemLocations.get(contentType).containsKey(presentationType) ) {
                        itemLocations.get(contentType).put(presentationType, new ArrayList<>());
                    }
                    itemLocations.get(contentType).get(presentationType).add(location);
                }
            }
        }
        return itemLocations;
    }

    private HashSet<String> getExternalItemListing( ObjectListing objectListing ) {
        HashSet<String> itemLocations = new HashSet<>();
        for( S3ObjectSummary obj : objectListing.getObjectSummaries() ) {
            String internalPath = obj.getKey();
            String externalPath = makeExternalLocation(internalPath);
            if( externalPath != null ) {
                itemLocations.add(externalPath);
                log.debug("External path was " + externalPath);
            } else {
                log.info("External path for object list was null?");
            }
        }
        return itemLocations;
    }

    /**
     * Translates an internal S3 path to the path used in the external API
     * @param internalPath
     * @return
     */
    private String makeExternalLocation( String internalPath ) {
        //"/root/" is not used in the external API, strip it off
        String[] itemPath = internalPath.split("/");
        if( itemPath.length > 2 ) {
            String externalPath = "";
            for (int i = 0; i < itemPath.length; ++i) {
                if( itemPath[i].equals("root")  && i < 2 ) {
                    continue;
                }
                if( itemPath[i].trim().length() == 0 ) {
                    continue;
                }
                externalPath += "/";
                externalPath += itemPath[i];
            }
            return externalPath;
        }
        return null;
    }

    public String getUserDir() {
        return ROOT_DIR + this.userName + "/" + this.userId + "/";
    }

    public String getItemDirectory(String contentType,
                                   ItemSchema.PresentationType presentationType) {
        return this.userDir + contentType + "/" + presentationType + "/";
    }

    public String getItemLocation( String name, String contentType,
                                   ItemSchema.PresentationType presentationType ) {
        return this.getItemDirectory( contentType, presentationType ) + name;
    }

    /**
     * This is not good enough. It slows things down, and still costs money.
     * Eventually, we should have an async task that updates a local cache of
     * used storage. If the cache says your below X of the limit (think atms),
     * you're good. Once you get up close, ping Amazon every time.
     * @param objectListing
     * @param maxSize
     * @return
     */
    private Response checkObjectListingSize( ObjectListing objectListing, int maxSize )
    {
        if( objectListing.isTruncated() && objectListing.getMaxKeys() >= maxSize ) {
            log.error("Error, too many uploads");
            return Response.status(418).entity("I'm a teapot! j/k - not enough space " + maxSize).build();
        }
        if( objectListing.getObjectSummaries().size() >= maxSize ) {
            log.error("Error, too many uploads");
            return Response.status(418).entity("I'm a teapot! Er, well, at least I can't hold " + maxSize + " stuff.").build();
        }
        return Response.status(Response.Status.OK).entity("OK").build();
    }

    private Response checkQuota() {
        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret));
        ObjectListing curList = s3Client.listObjects(ItemSchema.S3_BUCKET, this.getUserDir());
        Response listCheck;
        if ((listCheck = this.checkObjectListingSize(curList, Limits.MAX_NUM_FILES_PER_USER)).getStatus()
                != Response.Status.OK.getStatusCode()) {
            return listCheck;
        }
        ObjectListing userList = s3Client.listObjects(ItemSchema.S3_BUCKET, "/");
        if ((listCheck = this.checkObjectListingSize(userList, Limits.MAX_TOTAL_FILES)).getStatus()
                != Response.Status.OK.getStatusCode()) {
            return listCheck;
        }
        return Response.status(Response.Status.OK).build();
    }
}
