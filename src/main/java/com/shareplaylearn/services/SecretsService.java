package com.shareplaylearn.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;

/**
 * Created by stu on 4/19/15.
 */
public class SecretsService {
    public static String googleClientId;
    public static String googleClientSecret;
    public static String amazonClientId;
    public static String amazonClientSecret;
    public static String testOauthUsername;
    public static String testOauthPassword;
    private static final String GOOGLE_ID_KEY = "GoogleId";
    private static final String GOOGLE_SECRET_KEY = "GoogleSecret";
    private static final String AMAZON_ID_KEY = "AmazonId";
    private static final String AMAZON_SECRET = "AmazonSecret";
    private static final String TEST_OAUTH_USER_KEY = "TestOauthUser";
    private static final String TEST_OAUTH_PASSWORD_KEY = "TestOauthPassword";

    static {
        java.nio.file.Path secretsFile = FileSystems.getDefault().getPath("/etc/shareplaylearn.secrets");
        try {
            List<String> lines = Files.readAllLines(secretsFile, StandardCharsets.UTF_8);
            for( String line : lines ) {
                if( line.startsWith(GOOGLE_ID_KEY) ) {
                    googleClientId = getConfigValue(line);
                }
                else if( line.startsWith(GOOGLE_SECRET_KEY) ) {
                    googleClientSecret = getConfigValue(line);
                }
                else if(line.startsWith(AMAZON_ID_KEY)){
                    amazonClientId = getConfigValue(line);
                }
                else if(line.startsWith(AMAZON_SECRET)){
                    amazonClientSecret = getConfigValue(line);
                }
                else if(line.startsWith(TEST_OAUTH_USER_KEY)){
                    testOauthUsername = getConfigValue(line);
                }
                else if(line.startsWith(TEST_OAUTH_PASSWORD_KEY)){
                    testOauthPassword = getConfigValue(line);
                }

            }
            if( googleClientId == null ) {
                System.out.println("Warning: google client id not read in, oauth callback will fail");
            }
            if( googleClientSecret == null ) {
                System.out.println("Warning: google secret not read in, oauth callback will fail");
            }
            if( amazonClientId == null ) {
                System.out.println("Warning: Amazon client id not read in, data access will fail");
            }
            if( amazonClientSecret == null ) {
                System.out.println("Warning: Amazon client secret not read in, data access will fail");
            }
            //just to confirm this is the code that is indeed running on the server now.
            System.out.println("Secrets file read in.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Warning, failed to read secrets file, will not be able to login users or access stored data." + e.getMessage());
        }
    }

    public static String getConfigValue( String line )
    {
        String[] kv = line.split("=");
        if( kv.length != 2 ) {
            System.err.println("Warning: badly formatted line: " + line );
            return "";
        }
        return kv[1].trim();
    }

}
