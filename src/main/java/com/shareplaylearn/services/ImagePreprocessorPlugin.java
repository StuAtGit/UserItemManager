package com.shareplaylearn.services;

import com.shareplaylearn.exceptions.Exceptions;
import com.shareplaylearn.models.ItemSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by stu on 6/10/15.
 */
public class ImagePreprocessorPlugin
    implements UploadPreprocessorPlugin {

    public static final int PREVIEW_WIDTH = 200;
    public static final int RESIZE_LIMIT = 768;
    public float imageQuality;
    private String preferredFileExtension;
    private String previewFileExtension;
    //set when we calculate a preview
    private int lastPreviewHeight;
    //always set when we adjust the height,
    //just used for the methods to talk internally
    private int lastHeight;
    private static final String encodeMimeType = "image/jpeg";
    private final Logger log = LoggerFactory.getLogger(ImagePreprocessorPlugin.class);

    public ImagePreprocessorPlugin() {
        this.lastPreviewHeight = -1;
        this.lastHeight = -1;
        this.preferredFileExtension = "";
        this.previewFileExtension = "";
        this.imageQuality = 0.7f;
    }

    public ImagePreprocessorPlugin( float imageQuality ) {
        this.lastPreviewHeight = -1;
        this.lastHeight = -1;
        this.preferredFileExtension = "";
        this.previewFileExtension = "";
        this.imageQuality = imageQuality;
    }

    public float getImageQuality() {
        return imageQuality;
    }

    public ImagePreprocessorPlugin setImageQuality(float imageQuality) {
        this.imageQuality = imageQuality;
        return this;
    }

    @Override
    public String getPreferredFileExtension() {
        return this.preferredFileExtension;
    }

    @Override
    public String getPreviewFileExtension() {
        return this.previewFileExtension;
    }

    @Override
    public String getContentType() {
        return ItemSchema.IMAGE_CONTENT_TYPE;
    }

    @Override
    public synchronized boolean canProcess(byte[] fileBuffer) {
        try {
            return ImageIO.getImageReaders(toImageInputStream(fileBuffer)).hasNext();
        } catch (IOException e) {
            log.info("Error attempting to retrieve image readers: " + e.getMessage());
            log.debug(Exceptions.asString(e));
            return false;
        }
    }

    private synchronized BufferedImage getBufferedImage(byte[] fileBuffer) throws IOException {
        return ImageIO.read(this.toImageInputStream(fileBuffer));
    }

    private synchronized ImageInputStream toImageInputStream( byte[] fileBuffer ) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBuffer);
        return ImageIO.createImageInputStream(byteArrayInputStream);
    }

    @Override
    public Map<ItemSchema.PresentationType, byte[]> process(byte[] fileBuffer) {
        HashMap<ItemSchema.PresentationType, byte[]> uploadList = new HashMap<>();

        uploadList.put(ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE, fileBuffer);

        int originalWidth = -1;
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = this.getBufferedImage(fileBuffer);
            originalWidth = bufferedImage.getWidth();
            byte[] previewBuffer = scaleImageToWidth(bufferedImage, PREVIEW_WIDTH);
            this.previewFileExtension = "jpg";
            uploadList.put(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE, previewBuffer);
            this.lastPreviewHeight = this.lastHeight;
        } catch( IOException e ) {
            log.error(Exceptions.asString(e));
        }

        if( bufferedImage != null && originalWidth > 0
                && originalWidth > RESIZE_LIMIT ) {
            try {
                byte[] modifiedBuffer = scaleImageToWidth(bufferedImage, RESIZE_LIMIT);
                uploadList.put(ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE,modifiedBuffer);
                this.preferredFileExtension = "jpg";
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return uploadList;
    }

    private byte[] scaleImageToWidth(BufferedImage bufferedImage, int targetWidth) throws IOException {
        //first, ensure we have a jpg writer
        Iterator<ImageWriter> writers;
        ImageWriter imageWriter;
        if( (writers = ImageIO.getImageWritersByMIMEType(encodeMimeType)) != null
                && writers.hasNext()) {
            imageWriter = writers.next();
        } else {
            throw new IOException("Error retrieving image writer, no writers returned.");
        }

        //next, calculate the height
        //using the ratio from the target width && existing width.
        double scaleRatio = (double)targetWidth / (double)bufferedImage.getWidth();
        int newHeight = (int)(scaleRatio*bufferedImage.getHeight());
        this.lastHeight = newHeight;

        log.debug("Scaled image ratio: " + scaleRatio);
        log.debug("Target width: " + targetWidth);
        log.debug("Original height: " + bufferedImage.getHeight());
        log.debug("original width: " + bufferedImage.getWidth());
        log.debug("Scaled image height: " + newHeight);

        //draw our original image, and scale it, to a buffer.
        Image scaledImage = bufferedImage.getScaledInstance(targetWidth, newHeight, BufferedImage.SCALE_SMOOTH);
        BufferedImage scaledImageBuffer = new BufferedImage(targetWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        scaledImageBuffer.createGraphics().drawImage(scaledImage, 0, 0, null);

        //wrap the scaled image buffer in something an image writer will accept.
        IIOImage scaledImageContainer = new IIOImage(scaledImageBuffer, null, null);

        //set up our encoding parameters (jpg compression quality)
        ImageWriteParam imageParams = imageWriter.getDefaultWriteParam();
        imageParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        imageParams.setCompressionQuality(imageQuality);

        //prep the image writer with an appropriate output buffer
        ByteArrayOutputStream previewOutputStream = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream memoryCacheImageInputStream =
                new MemoryCacheImageOutputStream(previewOutputStream);
        imageWriter.setOutput(memoryCacheImageInputStream);

        imageWriter.write(scaledImageContainer);

        return previewOutputStream.toByteArray();
    }

    public int getLastPreviewHeight() {
        System.out.println("Returning a preview height of " + this.lastPreviewHeight);
        return this.lastPreviewHeight;
    }
}
