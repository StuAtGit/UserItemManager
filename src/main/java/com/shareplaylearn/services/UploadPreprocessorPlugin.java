package com.shareplaylearn.services;

import com.shareplaylearn.models.ItemSchema;

import java.io.IOException;
import java.util.Map;

/**
 * Created by stu on 6/10/15.
 */
public interface UploadPreprocessorPlugin {
    boolean canProcess(byte[] fileBuffer);
    //this returns a map of the presentation type (from the ItemSchema)
    //to the actual bytes. The presentations types are used to indicate
    //various transforms done on the original data customized for how it will be presented
    //(as a preview, as the actual thing, but adjusted (like resizing, but not for previewing),
    //or just the original bytes
    Map<ItemSchema.PresentationType,byte[]> process(byte[] fileBuffer);
    //this returns the file extension to use with any preferred presentation type transformation,
    //returns an empty string if the preferred is the original (no transformation)
    String getPreferredFileExtension();
    //this is the ItemSchema content type, not an HTTP content type
    String getContentType();
}
