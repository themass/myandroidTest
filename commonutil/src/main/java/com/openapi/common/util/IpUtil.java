package com.openapi.common.util;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.openapi.common.util.LogUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class IpUtil {
    private static String[] platforms = {
            "http://ip.chinaz.com/getip.aspx",
            "http://pv.sohu.com/cityjson",
            "http://pv.sohu.com/cityjson?ie=utf-8"

    };


    public static String getInNetIp(Context context) {
        //获取wifi服务
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        // 现代方式获取WiFi IP地址
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 使用现代方式
            try {
                // 使用ConnectivityManager获取网络信息
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    android.net.Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        android.net.LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
                        if (linkProperties != null) {
                            for (android.net.LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                                java.net.InetAddress address = linkAddress.getAddress();
                                if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                                    return address.getHostAddress();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 如果现代方式失败，回退到旧方式
            }
        }
        
        // 回退到旧方式（已废弃但兼容）
        if (!wifiManager.isWifiEnabled()) {
            @SuppressWarnings("deprecation")
            boolean enabled = wifiManager.setWifiEnabled(true);
        }

        @SuppressWarnings("deprecation")
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        @SuppressWarnings("deprecation")
        int ipAddress = wifiInfo.getIpAddress();
        String ip = intToIp(ipAddress);

        return ip;
    }

    //这段是转换成点分式IP的码
    private static String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + (ip >> 24 & 0xFF);
    }

    public static String getDomain(String urlPath) {
        try {
            URL url = new URL(urlPath);
            return url.getHost();
        } catch (Exception e) {
            LogUtil.e(e);
            return "";
        }
    }
}
