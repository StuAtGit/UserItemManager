package com.shareplaylearn.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Created by stu on 6/29/15.
 */
public class UserItem {

    //this field is for backward-compability with current UI code
    //once the UI is updated, remove it.
    //it should be set to the preferred location, unless that does not exist,
    //and then it should be set to the original location
    private String itemLocation;
    private String preferredLocation;
    private String previewLocation;
    private String originalLocation;
    private String type;
    private HashMap<String,String> attr;
    //looks like Gson doesn't know what to do with this, if it's an instance field
    private static Logger log = LoggerFactory.getLogger(UserItem.class);

    public UserItem(String type) {
        this.previewLocation = null;
        this.originalLocation = null;
        this.preferredLocation = null;
        this.itemLocation = null;
        this.type = type;
        this.attr = new HashMap<>();
        this.log = LoggerFactory.getLogger(UserItem.class);
    }

    public UserItem(String preferredLocation, String previewLocation, String originalLocation, String type) {
        this.previewLocation = previewLocation;
        this.originalLocation = originalLocation;
        this.preferredLocation = preferredLocation;
        this.type = type;
        this.attr = new HashMap<>();
        this.log = LoggerFactory.getLogger(UserItem.class);
    }

    public String getPreviewLocation() {
        return previewLocation;
    }

    public UserItem setLocation(ItemSchema.PresentationType presentationType, String location ) {
        if( presentationType.equals(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE) ) {
            return this.setPreviewLocation(location);
        } else if( presentationType.equals(ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE) ) {
            if( this.itemLocation == null ) {
                this.itemLocation = location;
            }
            if( this.preferredLocation == null ) {
                this.preferredLocation = location;
            }
            return this.setOriginalLocation(location);
        } else if( presentationType.equals(ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE) ) {
            this.itemLocation = location;
            return this.setPreferredLocation(location);
        } else {
            String message = "Tried to set location with an unrecognized presentation type";
            log.warn( message );
            throw new IllegalArgumentException( message );
        }
    }

    private UserItem setPreviewLocation(String previewLocation) {
        this.previewLocation = previewLocation;
        return this;
    }

    public String getPreferredLocation() {
        return preferredLocation;
    }

    private UserItem setPreferredLocation(String preferredLocation) {
        this.preferredLocation = preferredLocation;
        return this;
    }

    public String getType() {
        return type;
    }

    public UserItem setType(String type) {
        this.type = type;
        return this;
    }

    public UserItem addAttr(String key, String value) {
        this.attr.put(key, value);
        return this;
    }

    public String getOriginalLocation() {
        return originalLocation;
    }

    private UserItem setOriginalLocation(String originalLocation) {
        this.originalLocation = originalLocation;
        return this;
    }
}
