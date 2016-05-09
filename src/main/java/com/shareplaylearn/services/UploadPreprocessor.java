package com.shareplaylearn.services;

import com.shareplaylearn.models.ItemSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by stu on 6/10/15.
 * This class is built on the idea that an upload may have a:
 *
 *   - preview, a transformation of the contents that we'd liked to show in-line
 *   instead of the original content.
 *   - a "preferred" transformation - one that we return by default unless the user
 *   specifically wants the original
 *   - the original contents.
 *
 *   We will always have the original contents, and sometimes the original content
 *   will be the preferred content. We may or may not have a preview.
 *
 *   Note this also serves as the default upload processor plugin, as well as the overall
 *   upload processor that dispatches to the first processor plugin that claims it can handle the given bytes.
 *
 *   Example:
 *   An image may:
 *     - be resized, and we may wish to return the resized image as the a "preferred" content, in the case of a large
 *     image
 *     - have a preview size that we will embed in an img tag in-line in a list of items
 *
 *   Same thing could apply to long segments of text, html, markdown, video, etc.
 */
public class UploadPreprocessor
    implements UploadPreprocessorPlugin {

    List<UploadPreprocessorPlugin> uploadPreprocessorPluginList;
    private UploadPreprocessorPlugin processorPluginUsed;

    public UploadPreprocessor( List<UploadPreprocessorPlugin> preprocessorPluginList ) {
        this.uploadPreprocessorPluginList = preprocessorPluginList;
        this.processorPluginUsed = null;
    }

    @Override
    public boolean canProcess(byte[] fileBuffer) {
        return true;
    }

    public UploadPreprocessorPlugin getProcessorPluginUsed() {
        return processorPluginUsed;
    }

    @Override
    public Map<ItemSchema.PresentationType, byte[]> process(byte[] fileBuffer) {
        for( UploadPreprocessorPlugin p : this.uploadPreprocessorPluginList ) {
            if( p.canProcess(fileBuffer) ) {
                Map<ItemSchema.PresentationType,byte[]> uploadList = p.process(fileBuffer);
                this.processorPluginUsed = p;
                return uploadList;
            }
        }
        Map<ItemSchema.PresentationType,byte[]> defaultList = new HashMap<>();
        defaultList.put(ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE, fileBuffer);
        this.processorPluginUsed = this;
        return defaultList;
    }

    @Override
    public String getPreferredFileExtension() {
        return "";
    }

    @Override
    public String getContentType() {
        return ItemSchema.UNKNOWN_CONTENT_TYPE;
    }
}
