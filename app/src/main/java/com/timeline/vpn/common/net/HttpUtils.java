package com.timeline.vpn.common.net;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

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
    public  static Map<String, String> getHeader(){
        Map<String, String> header = new HashMap<>();
        return header;
    }
    public static final int ping(String ip) {
        String result = null;
        try {
            Process p = Runtime.getRuntime().exec("ping -c 2 -w 100 " + ip);
            InputStream input = p.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            StringBuffer stringBuffer = new StringBuffer();
            String content = "";
            while ((content = in.readLine()) != null) {
                stringBuffer.append(content);
            }
            Log.i("TTT", "result content : " + stringBuffer.toString());
            int status = p.waitFor();
            if (status == 0) {
                result = "successful~";
                return 1;
            } else {
                result = "failed~ cannot reach the IP address";
            }
        } catch (IOException e) {
            result = "failed~ IOException";
        } catch (InterruptedException e) {
            result = "failed~ InterruptedException";
        } finally {
            Log.i("TTT", "result = " + result);
        }
        return -1;
    }


}
