package com.shareplaylearn.exceptions;

/**
 * Created by stu on 7/10/16.
 */
public class QuotaExceededException
    extends Exception {

    public QuotaExceededException( String msg ) {
        super( msg );
    }
}
