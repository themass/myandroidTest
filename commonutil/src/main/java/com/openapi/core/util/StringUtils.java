package com.openapi.core.util;

import android.text.TextUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sssong on 14-03-12.
 */
public class StringUtils {
    public static final String DEFAULT_VALUE = "-";
    private static final String PERCENT_TAG = "%";
    private static final String SCALE_WAN = "万";
    private static final String SCALE_YI = "亿";
    private static final String SCALE_WAN_YI = "万亿";
    private static final int ONE = 1;
    private static final int WAN = 10000;
    private static final int YI = 100000000;
    private static final long WAN_YI = 1000000000000L;

    /**
     * 格式化百分比，默认：正数不保留+号，不自动转换单位
     *
     * @param value     百分比原数值
     * @param precision 小数精度位数
     * @return String
     */
    public static String formatPercent(Double value, int precision) {
        return formatPercent(value, precision, false, false);
    }

    /**
     * 格式化数字，默认：正数不保留+号，自动转换单位
     *
     * @param value     百分比原数值
     * @param precision 小数精度位数
     * @return String
     */
    public static String formatDouble(Double value, int precision) {
        return formatDouble(value, precision, false, true);
    }

    /**
     * 格式化百分比
     *
     * @param value               百分比原数值
     * @param precision           小数精度位数
     * @param isAlwaryIncludeSign 正数是否也保留+号
     * @param isScale             是否自动转换单位，如：12000 变成 1.2万
     * @return String
     */
    public static String formatPercent(Double value, int precision,
                                       boolean isAlwaryIncludeSign, boolean isScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }
        return formatDouble(value * 100, precision, isAlwaryIncludeSign,
                isScale) + PERCENT_TAG;
    }

    /**
     * 格式化数字
     *
     * @param value               百分比原数值
     * @param precision           小数精度位数
     * @param isAlwaryIncludeSign 正数是否也保留+号
     * @param isScale             是否自动转换单位，如：12000 变成 1.2万
     * @return String
     */
    public static String formatDouble(Double value, int precision,
                                      boolean isAlwaryIncludeSign, boolean isScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }

        StringBuilder format = new StringBuilder();
        format.append("%");
        if (isAlwaryIncludeSign) {
            format.append("+");
        }
        format.append(".").append(precision).append("f");

        if (isScale) {
            double absValue = Math.abs(value);
            if (absValue >= WAN_YI) {
                value = value / WAN_YI;
                format.append(SCALE_WAN_YI);
            } else if (absValue >= YI) {
                value = value / YI;
                format.append(SCALE_YI);
            } else if (absValue >= WAN) {
                value = value / WAN;
                format.append(SCALE_WAN);
            }
        }

        return String.format(format.toString(), value);
    }






    /**
     * 大小写转换 大写变小写 小写边大写 只针对key(全部为大写 或全部为小写)
     */
    public static String upLow(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        String covString;
        Pattern p = Pattern.compile("[A-Z]+");
        Matcher m1 = p.matcher(key); // 判断是否含有大写字符
        if (m1.find()) {
            covString = key.toLowerCase();// 大变小
        } else {
            covString = key.toUpperCase();
        }
        return covString;

    }


    public static boolean hasText(String str) {
        return str != null && str.trim().length() > 0;
    }




    public static boolean isEmpty(String name) {
        return name == null || name.trim().length() == 0;
    }
}
