package com.timeline.vpn.common.util;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonUtil {
    private static String PAID_INFO_URL = "http://i.money.163.com/app/api/info/p_[A-Z0-9]+_[A-Z0-9]+\\.html";
    private static String PAID_INFO_ID_STR = "p_[A-Z0-9]+_[A-Z0-9]+";
    private static String FREE_INFO_URL = "http://m.money.163.com/news/[0-9]+/[0-9]+/[0-9]+/[A-Z0-9]{16}.html";
    private static String INFO_ID_STR = "[A-Z0-9]{16}";

    public static boolean isEmpty(Collection<? extends Object> coll) {
        if (coll != null && coll.size() > 0) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isEmpty(Map<? extends Object, ? extends Object> map) {
        if (map != null && map.size() > 0) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isPaidInfo(String url) {
        Pattern pattern = Pattern.compile(PAID_INFO_URL);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return true;
        }
        return false;
    }

    public static boolean isFreeInfo(String url) {
        Pattern pattern = Pattern.compile(FREE_INFO_URL);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return true;
        }
        return false;
    }

    public static String getDocIdFromUrl(String url) {
        Pattern pattern = Pattern.compile(INFO_ID_STR);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    public static String getInfoIdFromUrl(String url) {
        Pattern pattern = Pattern.compile(PAID_INFO_ID_STR);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }
}
