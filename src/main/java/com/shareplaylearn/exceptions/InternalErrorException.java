package com.shareplaylearn.exceptions;

/**
 * Created by stu on 9/6/15.
 */
public class InternalErrorException extends Exception {
    public InternalErrorException( String reason ) {
        super(reason);
    }
}
