package com.openapi.common.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import com.openapi.common.util.LogUtil;
@SuppressWarnings("deprecation")
public class NetUtils {

    /**
     * 获取网络类型
     *
     * @param context
     * @return
     */
    /**
     * 判断当前网络是否是wifi
     *
     * @param context
     * @return
     */
    public static boolean isWifi(Context context) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 使用现代API
                android.net.Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                    return capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                }
            } else {
                // 兼容旧版本
                NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetInfo != null) {
                    int type = activeNetInfo.getType();
                    boolean isWifi = type == ConnectivityManager.TYPE_WIFI;
                    return isWifi;
                }
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return false;
    }
}

