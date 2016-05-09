package com.shareplaylearn.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by stu on 5/5/15.
 */
public class Exceptions {

    public static String asString( Throwable t ) {
        StringWriter sw = new StringWriter();
        PrintWriter pw  = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
