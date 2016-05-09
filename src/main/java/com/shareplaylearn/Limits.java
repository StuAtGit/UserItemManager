package com.shareplaylearn;

/**
 * Created by stu on 9/6/15.
 */
public class Limits {
    /**
     * With 2MB default upload limit on tomcat, this comes to about:
     * 1000*2MB ~ 2GB of data stored at any given time.
     * 1 req/sec ~ 3x10^6 requests a month.
     * At 10 GB / Month out, and 1 TB / Month in (digital ocean limits it to 1 TB, so that should throttle that).
     * https://calculator.s3.amazonaws.com/index.html says we should be spending around $24 max.
     */
    //per user
    public static final int MAX_NUM_FILES_PER_USER = 100;
    public static final int MAX_TOTAL_FILES = 1000;
    public static final int DEFAULT_ITEM_QUOTA = 100;
    public static final int IN_MEMORY_CACHE_LIMIT = 10;
    //limit retrieves to 0.5 GB for now (Tomcat should limit uploads to 2MB).
    //raise to 1 GB when we buy more memory (if needed)
    public static final int MAX_RETRIEVE_SIZE = (1024*1024*1024)/2;

}
