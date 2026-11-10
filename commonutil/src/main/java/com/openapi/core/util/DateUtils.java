package com.openapi.core.util;

import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * DateUtils
 */
@SuppressLint("SimpleDateFormat")
public class DateUtils {
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT_MM = "MMddHHmm";
    public static final String DATE_FORMAT = "yyyyMMdd";
    private static final long ONE_MINUTE = 60000L;
    private static final long ONE_HOUR = 3600000L;

    private static final String ONE_SECOND_AGO = "秒前";
    private static final String ONE_MINUTE_AGO = "分钟前";



    public static String format(Date date) {
        return format(date, DATETIME_FORMAT);
    }

    public static String format(Date date, String format) {
        return new SimpleDateFormat(format).format(date);
    }

    public static Date parse(String date) {
        return parse(date, DATETIME_FORMAT);
    }

    public static Date parse(String date, String format) {
        try {
            SimpleDateFormat sf = new SimpleDateFormat(format);
            return sf.parse(date);
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return null;
    }

    public static String format(String date, String format) {
        Date d = parse(date);
        if (d != null) {
            return format(d, format);
        }
        return "";
    }



    private static long toSeconds(long date) {
        return date / 1000L;
    }

    private static long toMinutes(long date) {
        return toSeconds(date) / 60L;
    }
}
