package com.shareplaylearn.services;

import com.shareplaylearn.exceptions.Exceptions;
import com.shareplaylearn.models.ItemSchema;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by stu on 6/10/15.
 */
public class ImagePreprocessorPlugin
    implements UploadPreprocessorPlugin {

    private byte[] imageBuffer;
    public static final int PREVIEW_WIDTH = 200;
    public static final int RESIZE_LIMIT = 1024;
    public static final String RESIZED_TAG = "resized";
    private String preferredFileExtension;
    //set when we calculate a preview
    private int lastPreviewHeight;
    //always set when we adjust the height,
    //just used for the methods to talk internally
    private int lastHeight;

    public ImagePreprocessorPlugin() {
        this.imageBuffer = null;
        this.lastPreviewHeight = -1;
        this.lastHeight = -1;
        this.preferredFileExtension = "";
    }

    @Override
    public String getPreferredFileExtension() {
        return this.preferredFileExtension;
    }

    @Override
    public String getContentType() {
        return ItemSchema.IMAGE_CONTENT_TYPE;
    }

    @Override
    public boolean canProcess(byte[] fileBuffer) {
        //this is a simple, but possibly slow method
        //first - to detect the file, it just checks if we have any readers for it
        //next - ImageIO.getScaledInstance is supposed to be a bit slow (but this info may be outdated?)
        //https://stackoverflow.com/questions/4220612/scaling-images-with-java-jai
        //https://github.com/thebuzzmedia/imgscalr/blob/master/src/main/java/org/imgscalr/Scalr.java
        try {
            return ImageIO.read(toImageInputStream(fileBuffer)) != null;
        } catch (IOException e) {
            System.out.println("Cannot processing filebuffer in Image Plugin because of exception " + e.getMessage());
            return false;
        }
    }

    @Override
    public Map<ItemSchema.PresentationType, byte[]> process(byte[] fileBuffer) {
        HashMap<ItemSchema.PresentationType, byte[]> uploadList = new HashMap<>();

        uploadList.put(ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE, fileBuffer);

        int originalWidth = -1;
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(this.toImageInputStream(fileBuffer));
            originalWidth = bufferedImage.getWidth();
            byte[] previewBuffer = shrinkImageToWidth(bufferedImage, PREVIEW_WIDTH);
            uploadList.put(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE, previewBuffer);
            this.lastPreviewHeight = this.lastHeight;
        } catch( IOException e ) {
            System.out.println(Exceptions.asString(e));
        }

        if( bufferedImage != null && originalWidth > 0
                && originalWidth > RESIZE_LIMIT ) {
            try {
                byte[] modifiedBuffer = shrinkImageToWidth(bufferedImage, RESIZE_LIMIT);
                uploadList.put(ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE,modifiedBuffer);
                this.preferredFileExtension = "jpg";
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return uploadList;
    }

    private ImageInputStream toImageInputStream( byte[] fileBuffer ) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBuffer);
        try {
            return ImageIO.createImageInputStream(byteArrayInputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] shrinkImageToWidth( BufferedImage bufferedImage, int targetWidth) throws IOException {
        double previewRatio = (double)targetWidth / (double)bufferedImage.getWidth();
        System.out.println("preview ratio: " + previewRatio);
        System.out.println("Target width: " + targetWidth);
        System.out.println("Original height: " + bufferedImage.getHeight());
        System.out.println("original width: " + bufferedImage.getWidth());
        int previewHeight = (int)(previewRatio*bufferedImage.getHeight());
        this.lastHeight = previewHeight;
        System.out.println("preview height: " + previewHeight);
        Image scaledImage = bufferedImage.getScaledInstance(targetWidth, previewHeight, BufferedImage.SCALE_SMOOTH);
        BufferedImage preview = new BufferedImage(targetWidth, previewHeight, BufferedImage.TYPE_INT_RGB);
        preview.createGraphics().drawImage(scaledImage, 0, 0, null);
        ByteArrayOutputStream previewOutputStream = new ByteArrayOutputStream();
        ImageIO.write(preview, "jpg", previewOutputStream);
        return previewOutputStream.toByteArray();
    }

    public int getLastPreviewHeight() {
        System.out.println("Returning a preview height of " + this.lastPreviewHeight);
        return this.lastPreviewHeight;
    }
}
