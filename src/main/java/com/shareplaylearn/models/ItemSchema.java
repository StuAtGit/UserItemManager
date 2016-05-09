package com.shareplaylearn.models;

/**
 * Created by stu on 9/7/15.
 */
public class ItemSchema {
    /**
     * These really shouldn't much change over time (if at all)
     * And represent the context in which this instance of an item should be presented.
     * Something like "mobile" or "crappy_network :D" might be a good addition, though.
     * The Content Types will hopefully grow and grown! We'll see ;)
     */
    public enum PresentationType {
        PREVIEW_PRESENTATION_TYPE("preview"),
        ORIGINAL_PRESENTATION_TYPE("original"),
        PREFERRED_PRESENTATION_TYPE("preferred");
        private final String type;

        PresentationType(String type ) {
            this.type = type;
        }

        public String toString() {
            return this.type;
        }

        public static PresentationType fromString( String type ) {
            if( type.equals("preview") ) {
                return PREVIEW_PRESENTATION_TYPE;
            } else if( type.equals("original") ) {
                return ORIGINAL_PRESENTATION_TYPE;
            } else if( type.equals("preferred") ) {
                return PREFERRED_PRESENTATION_TYPE;
            } else {
                throw new IllegalArgumentException("Invalid presentation type: " + type);
            }
        }

    }

    public static final PresentationType[] PRESENTATION_TYPES = {
            PresentationType.PREVIEW_PRESENTATION_TYPE,
            PresentationType.ORIGINAL_PRESENTATION_TYPE,
            PresentationType.PREFERRED_PRESENTATION_TYPE
    };

    public static final String UNKNOWN_CONTENT_TYPE = "unknown";
    public static final String IMAGE_CONTENT_TYPE = "image";
    //while it would be nice to map these to RFC HTTP types
    //they wouldn't be as directory friendly (we could do it, but the directory structure might be a bit odd)
    //think about it though
    public static final String[] CONTENT_TYPES = {
            IMAGE_CONTENT_TYPE,
            UNKNOWN_CONTENT_TYPE
    };

    public static final String S3_BUCKET = "shareplaylearn";
}
