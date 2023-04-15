package com.openapi.common.util;

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
    public static final String DATE_MM_FORMAT = "yyyyMM";

    private static final long ONE_MINUTE = 60000L;
    private static final long ONE_HOUR = 3600000L;

    private static final String ONE_SECOND_AGO = "秒前";
    private static final String ONE_MINUTE_AGO = "分钟前";

    public static String formatDate(String src) {
        try {
            if (src != null && src.length() >= 10) {
                return src.substring(0, 10);
            } else {
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static long getHourSub(Date beginDate, Date endDate) {
        return (endDate.getTime() - beginDate.getTime()) / (60 * 60 * 1000);
    }

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
            e.printStackTrace();
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

    /**
     * 格式化显示日期
     */
    public static String toDisplayDate(Date date) {
        if (date == null) {
            return "";
        }

        Calendar dateCalendar = Calendar.getInstance();
        dateCalendar.setTime(date);

        Calendar nowCalendar = Calendar.getInstance();
        nowCalendar.set(Calendar.HOUR_OF_DAY, 0);
        nowCalendar.set(Calendar.MINUTE, 0);
        nowCalendar.set(Calendar.SECOND, 0);
        nowCalendar.set(Calendar.MILLISECOND, 0);

        if (dateCalendar.after(nowCalendar) || dateCalendar.equals(nowCalendar)) {
            // 日期为今天: 15:30
            return "今天";
        } else {
            nowCalendar.set(Calendar.MONTH, 0);
            nowCalendar.set(Calendar.DAY_OF_MONTH, 1);

            if (dateCalendar.after(nowCalendar) || dateCalendar.equals(nowCalendar)) {
                // 日期不是今天，但是今年: 11-19
                return format(date, "MM-dd");
            } else {
                // 日期不是今年: 2012-11-13
                return format(date, "yyyy-MM-dd");
            }
        }

    }

    /**
     * 格式化显示日期+时间，显示x秒前、x分前。。。的时间格式
     */
    public static String toDisplayDatetime(Date date) {
        if (date == null) {
            return "";
        }

        long delta = System.currentTimeMillis() - date.getTime();
        if (delta < ONE_MINUTE) {
            // x秒前
            long seconds = toSeconds(delta);
            return (seconds <= 0 ? 1 : seconds) + ONE_SECOND_AGO;
        } else if (delta < ONE_HOUR) {
            // x分钟前
            long minutes = toMinutes(delta);
            return (minutes <= 0 ? 1 : minutes) + ONE_MINUTE_AGO;
        } else {
            Calendar dateCalendar = Calendar.getInstance();
            dateCalendar.setTime(date);

            Calendar nowCalendar = Calendar.getInstance();
            nowCalendar.set(Calendar.HOUR_OF_DAY, 0);
            nowCalendar.set(Calendar.MINUTE, 0);
            nowCalendar.set(Calendar.SECOND, 0);
            nowCalendar.set(Calendar.MILLISECOND, 0);

            if (dateCalendar.after(nowCalendar) || dateCalendar.equals(nowCalendar)) {
                // 日期为今天: 15:30
                return format(date, "HH:mm");
            } else {
                nowCalendar.set(Calendar.MONTH, 0);
                nowCalendar.set(Calendar.DAY_OF_MONTH, 1);

                if (dateCalendar.after(nowCalendar) || dateCalendar.equals(nowCalendar)) {
                    // 日期不是今天，但是今年: 11-19 15:30
                    return format(date, "MM-dd HH:mm");
                } else {
                    // 日期不是今年: 2012-11-13 15:20
                    return format(date, "yyyy-MM-dd HH:mm");
                }
            }
        }
    }

    private static long toSeconds(long date) {
        return date / 1000L;
    }

    private static long toMinutes(long date) {
        return toSeconds(date) / 60L;
    }
}
