package com.shareplaylearn;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.Base64;
import com.shareplaylearn.exceptions.*;
import com.shareplaylearn.exceptions.UnsupportedEncodingException;
import com.shareplaylearn.models.ItemSchema;
import com.shareplaylearn.models.UploadMetadataFields;
import com.shareplaylearn.models.UserItem;
import com.shareplaylearn.services.ImagePreprocessorPlugin;
import com.shareplaylearn.services.SecretsService;
import com.shareplaylearn.services.UploadPreprocessor;
import com.shareplaylearn.services.UploadPreprocessorPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
@SuppressWarnings("WeakerAccess")
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

    public void addItem( String name, byte[] item )
            throws InternalErrorException, QuotaExceededException {
        this.checkQuota();

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
                        this.saveItemAtLocation(name.substring(0, extIndex) + preferredExtension,
                                uploadEntry.getValue(), contentType, presentationType);
                    } else {
                        this.saveItemAtLocation(name + preferredExtension, uploadEntry.getValue(), contentType, presentationType);
                    }
                } else {
                    this.saveItemAtLocation(name, uploadEntry.getValue(), contentType, presentationType);
                }
            } else {
                log.error( "Upload plugin had an entry with a presentation type of: " + presentationType
                        + " that was not found in the item types defined in the ItemSchema.");
            }
        }
    }

    /**
     * Delete sub-item/representation of the item at the given individual location.
     * @param contentType
     * @param presentationType
     * @param itemName
     * @return
     * @throws AmazonClientException
     */
    public boolean deleteItemAtLocation(String contentType, ItemSchema.PresentationType presentationType, String itemName )
            throws AmazonClientException {
        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret)
        );

        String itemLocation =  getItemLocation(itemName, contentType, presentationType);
        if (!s3Client.doesObjectExist(ItemSchema.S3_BUCKET,
               itemLocation)) {
            log.debug("Did not find item at: " + getItemLocation(itemName, contentType, presentationType));
            return false;
        }
        s3Client.deleteObject(ItemSchema.S3_BUCKET,
                itemLocation);
        log.debug("Deleted item at: " + itemLocation);
        return true;
    }

    /**
     *
     * @param contentType - the content type of the object - this value is given in the file listing.
     *                      examples: image, unknown
     * @param presentationType - which version of the object do you want? Preview, Original, etc
     * @param name - the name of the object
     * @param encoding  - how you would like the returned bytes encoded [null, empty string] => "identity"
     *                      or "base64". If base64, returns the UTF-8 encoded bytes corresponding to the base64 encoded
     *                      string of the objects bytes (e.g., sometimes useful in web ui's).
     * @return
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public byte[] getItem(String contentType, ItemSchema.PresentationType presentationType,
                            String name, String encoding ) throws UnsupportedEncodingException, IOException {

        if( encoding != null && encoding.length() > 0 && !AvailableEncodings.isAvailable(encoding) ) {
            throw new UnsupportedEncodingException( "Requested Encoding Type: " + encoding + " for item: "
                    + name + "  not available");
        }

        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret)
        );

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
            int bytesRead;
            int totalBytesRead = 0;
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            log.debug("GET in file resource read: " + totalBytesRead + " bytes.");
            if( encoding == null || encoding.length() == 0 || encoding.equals(AvailableEncodings.IDENTITY) ) {
                return outputStream.toByteArray();
            } else if( encoding.equals(AvailableEncodings.BASE64) ) {
                return Base64.encodeAsString(outputStream.toByteArray()).getBytes(StandardCharsets.UTF_8);
            } else {
                throw new UnsupportedEncodingException("Encoding: " + encoding + " not supported for item:" +
                        "" + name);
            }
        }
    }

    /**
     * Writes items to S3, and item metadata to Redis
     */
    private void saveItemAtLocation(String name, byte[] itemData, String contentType, ItemSchema.PresentationType presentationType  )
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
        HashMap<String,HashMap<ItemSchema.PresentationType,List<UserItem.UserItemLocation>>> itemLocations
                = getItemLocations();
        List<UserItem> itemList = new ArrayList<>();
        HashMap<String,UserItem> userItems = new HashMap<>();

        for( Map.Entry<String, HashMap<ItemSchema.PresentationType, List<UserItem.UserItemLocation>>> items
                : itemLocations.entrySet() ) {
            String contentType = items.getKey();

            for( Map.Entry<ItemSchema.PresentationType, List<UserItem.UserItemLocation>> item
                    : items.getValue().entrySet() ) {
                ItemSchema.PresentationType presentationType = item.getKey();

                List<UserItem.UserItemLocation> locations = item.getValue();
                for( UserItem.UserItemLocation location : locations ) {
                    //workaround (see above)
                    int extIndex = location.itemName.lastIndexOf(".");
                    //the display name servers as the item key, that associates
                    //different locations together as the same item.
                    String itemKey = "";
                    if( extIndex > 0 ) {
                        itemKey = location.itemName.substring(0, extIndex);
                    }
                    log.debug("Got a location: " + location + " for item with display name: " + itemKey + " for user: " + this.userName);
                    UserItem userItem = null;
                    if (!userItems.containsKey(itemKey)) {
                        userItems.put(itemKey, new UserItem(contentType));
                    }
                    userItem = userItems.get(itemKey);
                    userItem.setLocation(presentationType, location);
                    if (presentationType.equals(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE)) {
                        userItem.addAttr("altText", "Preview of " + itemKey);
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

    public HashMap<String,HashMap<ItemSchema.PresentationType,List<UserItem.UserItemLocation>>> getItemLocations() {

        HashMap<String,HashMap<ItemSchema.PresentationType,List<UserItem.UserItemLocation>>> itemLocations
                = new HashMap<>();

        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret));

        for( String contentType : ItemSchema.CONTENT_TYPES ) {
            for( ItemSchema.PresentationType presentationType : ItemSchema.PRESENTATION_TYPES ) {

                ObjectListing listing = s3Client.listObjects( ItemSchema.S3_BUCKET,
                        this.getItemDirectory(contentType, presentationType) );

                HashSet<UserItem.UserItemLocation> locations = getExternalItemListing(listing);
                String curDirectory = makeExternalLocation(getItemDirectory(contentType, presentationType)).fullPath;
                for( UserItem.UserItemLocation location : locations ) {
                    //it would be nice if s3 didn't return stuff that doesn't technically match the prefix
                    //(due to trailing /), but it looks like it might
                    if( curDirectory.endsWith(location.itemName) ) {
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

    /**
     * Gets a listing of items in S3, and translates the locations to those
     * recgonized by the external item API (used in the RESTful interface)
     * @param objectListing
     * @return
     */
    private HashSet<UserItem.UserItemLocation> getExternalItemListing(ObjectListing objectListing ) {
        HashSet<UserItem.UserItemLocation> itemLocations = new HashSet<>();
        for( S3ObjectSummary obj : objectListing.getObjectSummaries() ) {
            String internalPath = obj.getKey();
            UserItem.UserItemLocation externalLocation = makeExternalLocation(internalPath);
            if( externalLocation != null ) {
                itemLocations.add(externalLocation);
                log.debug("External path was " + externalLocation);
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
    private UserItem.UserItemLocation makeExternalLocation(String internalPath ) {
        //"/root/" is not used in the external API, strip it off
        String[] itemPath = internalPath.split("/");
        if( itemPath.length > 2 ) {
            String externalPath = "";
            String itemName = "";
            for (int i = 0; i < itemPath.length; ++i) {
                if( itemPath[i].equals("root")  && i < 2 ) {
                    continue;
                }
                if( itemPath[i].trim().length() == 0 ) {
                    continue;
                }
                externalPath += "/";
                externalPath += itemPath[i];
                //just keep updating this with valid entries
                //don't want to use length() and end up with empty dirs or root dirs
                itemName = itemPath[i];
            }
            return new UserItem.UserItemLocation(externalPath, itemName);
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
    private boolean isListingMaxExceeded( ObjectListing objectListing,
                                          AmazonS3Client s3Client, int maxSize )
    {
        long totalNumFiles = objectListing.getObjectSummaries().size();
        if( totalNumFiles >= maxSize ) {
            log.error("Error, too many uploads");
            return true;
        }

        int numListings = 0;
        while( objectListing.isTruncated()) {
            objectListing = s3Client.listNextBatchOfObjects(objectListing);
            totalNumFiles += objectListing.getObjectSummaries().size();
            numListings++;
            if( totalNumFiles >= maxSize ) {
                log.error("Error, too many uploads, performed: " + numListings + " listings.");
                return true;
            }
        }
        return false;
    }

    private void checkQuota()
        throws QuotaExceededException  {
        AmazonS3Client s3Client = new AmazonS3Client(
                new BasicAWSCredentials(SecretsService.amazonClientId, SecretsService.amazonClientSecret));
        ObjectListing curList = s3Client.listObjects(ItemSchema.S3_BUCKET, this.getUserDir());
        if (isListingMaxExceeded(curList, s3Client, Limits.MAX_NUM_FILES_PER_USER)) {
            throw new QuotaExceededException("Too many items stored for user, exceeded max files per user: " +
                Limits.MAX_NUM_FILES_PER_USER);
        }
        if (isListingMaxExceeded(curList, s3Client, Limits.MAX_TOTAL_FILES)) {
            throw new QuotaExceededException("Too many items stored for user, exceeded max files for the whole system: " +
                    Limits.MAX_TOTAL_FILES);
        }
    }
}
