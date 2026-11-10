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







    public static boolean hasText(String str) {
        return str != null && str.trim().length() > 0;
    }




    public static boolean isEmpty(String name) {
        return name == null || name.trim().length() == 0;
    }
}
