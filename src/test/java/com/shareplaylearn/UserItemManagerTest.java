package com.shareplaylearn;

import com.google.gson.Gson;
import com.shareplaylearn.exceptions.InternalErrorException;
import com.shareplaylearn.exceptions.QuotaExceededException;
import com.shareplaylearn.models.ItemSchema;
import com.shareplaylearn.models.UserItem;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Unit test for simple UserItemManager.
 */
public class UserItemManagerTest
{
    private UserItemManager userItemManager;
    private static Random random = new Random();
    private static final String[] testFiles = { "test_jpegs/LuckyWaitress.png", "test_jpegs/pctechsupportcat.jpg", "test_jpegs/Disneyland.jpg" };
    private static final Logger log = LoggerFactory.getLogger(UserItemManagerTest.class);

    public UserItemManagerTest() {
        String unitTestUser = "unit_test_user";
        String unitTestUserId = Integer.toString(random.nextInt());
        userItemManager = new UserItemManager( unitTestUser, unitTestUserId );
    }

    @Test
    public void testGetFileList() {
        List<UserItem> userItemList = userItemManager.getItemList();
        Gson gson = new Gson();
        log.debug("user item list was: " + gson.toJson(userItemList));
    }

    @Test
    public void addUserItem() throws IOException, InternalErrorException, QuotaExceededException {
        for( String testFile : testFiles ) {
            Path testFilePath = FileSystems.getDefault().getPath(testFile);
            if( !Files.exists(testFilePath) ) {
                log.warn("Missing test file!! " + testFilePath);
                continue;
            }
            byte[] testFileBytes = Files.readAllBytes(testFilePath);
            String filename = testFilePath.getFileName().toString();
            log.debug("Testing upload of: " + filename);
            userItemManager.addItem(filename, testFileBytes);
            List<UserItem> userItemList = userItemManager.getItemList();
            Gson gson = new Gson();
            log.debug("user item list is now: " + gson.toJson(userItemList));
            boolean found = false;
            for (UserItem userItem : userItemList) {
                if (filename.startsWith(userItem.getAttr("display_name")) &&
                        userItem.getType().equals("image")) {
                    found = true;
                    System.out.println(
                            "Deleted: " + userItemManager.deleteItemAtLocation(userItem.getType(),
                                    ItemSchema.PresentationType.ORIGINAL_PRESENTATION_TYPE,
                                    userItem.getOriginalLocation().itemName)
                    );
                    System.out.println(
                            "Deleted: " + userItemManager.deleteItemAtLocation(userItem.getType(),
                                    ItemSchema.PresentationType.PREVIEW_PRESENTATION_TYPE,
                                    userItem.getPreviewLocation().itemName)
                    );
                    System.out.println(
                            "Deleted: " + userItemManager.deleteItemAtLocation(userItem.getType(),
                                    ItemSchema.PresentationType.PREFERRED_PRESENTATION_TYPE,
                                    userItem.getPreferredLocation().itemName)
                    );
                    break;
                }
            }
            assertTrue(found);
        }

    }
}
