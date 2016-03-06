package com.timeline.vpn.common.util;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;


/**
 * Author: cyning
 * Date  : 2015.06.25
 * Time  : 下午3:16
 * Desc  : 获取设备相关的信息
 */
public class DeviceInfoUtils {


    /**
     * 基于Android3.0的平板
     *
     * @param context
     * @return
     */
    public static boolean isHoneycombTablet(Context context) {
        return AndroidVersionUtils.hasHoneycomb() && isTablet(context);
    }

    /**
     * 判断是否为平板设备
     *
     * @param context
     * @return
     */
    public static boolean isTablet(Context context) {
        // 暂时屏蔽平板
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
            // return (context.getResources().getConfiguration().screenLayout
            // & Configuration.SCREENLAYOUT_SIZE_MASK)
            // >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        }
        return false;
    }

    /**
     * 获取设备ID.
     *
     * @param context
     * @return
     */
    public static String getDeviceId(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            return tm.getDeviceId();
        }
        return null;
    }

    /**
     * 获取序列号
     *
     * @param context
     * @return
     */
    public static String getSimSerialNumber(Context context) {
        return Build.SERIAL;
    }

    /**
     * 获取手机mac地址
     *
     * @param context
     * @return
     */
    public static String getMac(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi.getConnectionInfo();
        return info.getMacAddress();
    }


    /**
     * 获取设备名称.
     *
     * @return
     */
    public static String getBuildModel() {
        return Build.MODEL;
    }

}
