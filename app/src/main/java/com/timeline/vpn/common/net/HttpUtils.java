package com.timeline.vpn.common.net;

import android.content.Context;

public class HttpUtils {
    private static String USER_AGENT_SUFFIX = "";

    public static boolean isGzip(String name, String value) {
        if ("Content-Encoding".equals(name) && "gzip".equals(value)) {
            return true;
        }
        return false;
    }
    public static boolean isGzip( String value) {
        if ( "gzip".equals(value)) {
            return true;
        }
        return false;
    }

    public static String getUserAgentSuffix(Context context) {
        return USER_AGENT_SUFFIX;
    }
}
