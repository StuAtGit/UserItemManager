package com.shareplaylearn.models;

/**
 * Created by stu on 6/10/15.
 */
public class UploadMetadataFields {
    /**
     * We should convert all of these to lower case, since
     * it looks like amazon does this anyways.
     * But prolly best to reset the metadata when we do.
     */
    public static final String PUBLIC = "public";
    public static String DISPLAY_NAME = "display_name";
    public static String TRUE_VALUE = "true";
    public static String FALSE_VALUE = "false";
    public static String CONTENT_TYPE = "type";
}