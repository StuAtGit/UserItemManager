package com.shareplaylearn.models;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Created by stu on 6/29/15.
 */
public class UserItem {

    public static class UserItemLocation {
        public UserItemLocation( String fullPath, String itemName ) {
            this.fullPath = fullPath;
            this.itemName = itemName;
        }
        public String toString() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
        public String fullPath;
        public String itemName;
    }

    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    private UserItemLocation preferredLocation;
    private UserItemLocation previewLocation;
    private UserItemLocation originalLocation;
    private String type;
    private HashMap<String,String> attr;
    //keep it static so Gson doesn't bother with it
    private static Logger log = LoggerFactory.getLogger(UserItem.class);

    public UserItem(String type) {
        this.previewLocation = null;
        this.originalLocation = null;
        this.preferredLocation = null;
        this.type = type;
        this.attr = new HashMap<>();
        this.log = LoggerFactory.getLogger(UserItem.class);
    }

    public UserItem(UserItemLocation preferredLocation,
                    UserItemLocation previewLocation,
                    UserItemLocation originalLocation, String type) {
        this.previewLocation = previewLocation;
        this.originalLocation = originalLocation;
        this.preferredLocation = preferredLocation;
        this.type = type;
        this.attr = new HashMap<>();
        this.log = LoggerFactory.getLogger(UserItem.class);
    }

    public UserItemLocation getPreviewLocation() {
        return previewLocation;
    }

    public UserItem setLocation(ItemSchema.PresentationType presentationType, UserItemLocation location ) {
        if( presentationType.equals(ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE) ) {
            return this.setPreviewLocation(location);
        } else if( presentationType.equals(ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE) ) {
            if( this.preferredLocation == null ) {
                this.preferredLocation = location;
            }
            return this.setOriginalLocation(location);
        } else if( presentationType.equals(ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE) ) {
            return this.setPreferredLocation(location);
        } else {
            String message = "Tried to set location with an unrecognized presentation type";
            log.warn( message );
            throw new IllegalArgumentException( message );
        }
    }

    private UserItem setPreviewLocation(UserItemLocation previewLocation) {
        this.previewLocation = previewLocation;
        return this;
    }

    public UserItemLocation getPreferredLocation() {
        return preferredLocation;
    }

    private UserItem setPreferredLocation(UserItemLocation preferredLocation) {
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

    public String getAttr( String key ) {
        if( this.attr.containsKey(key) ) {
            return this.attr.get(key);
        }
        return null;
    }

    public UserItemLocation getOriginalLocation() {
        return originalLocation;
    }

    private UserItem setOriginalLocation(UserItemLocation originalLocation) {
        this.originalLocation = originalLocation;
        return this;
    }
}
