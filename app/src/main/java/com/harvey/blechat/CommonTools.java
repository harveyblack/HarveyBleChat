package com.harvey.blechat;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CommonTools {
    public static String getTimeStamp(){
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat formatter = new SimpleDateFormat("mm:ss:SSS");
        Date date = new Date(currentTime);
        return formatter.format(date);
    }
}
