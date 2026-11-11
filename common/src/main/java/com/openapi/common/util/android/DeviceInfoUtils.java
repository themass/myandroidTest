package com.openapi.common.util.android;

import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.StringUtils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Author: cyning
 * Date  : 2015.06.25
 * Time  : 下午3:16
 * Desc  : 获取设备相关的信息
 */
public class DeviceInfoUtils {
    public static final String NULL = "null";

    /**
     * 获取设备ID.
     *
     * @param context
     * @return
     */
    public static String getDeviceId(Context context) {

        StringBuilder deviceId = new StringBuilder();
        // 渠道标志
        deviceId.append("android");

        try {
//            // wifi mac地址
//            String wifiMac = DeviceInfoUtils.getMac(context);
//            if (StringUtils.hasText(wifiMac)) {
//                deviceId.append("wifi");
//                deviceId.append(wifiMac);
//                return deviceId.toString();
//            }
//            // IMEI（imei）
//            String dev = DeviceInfoUtils.getImei(context);
//            if (StringUtils.hasText(dev)) {
//                deviceId.append("imei");
//                deviceId.append(dev);
//                return deviceId.toString();
//            }
//            // 序列号（sn）
//            String serial = DeviceInfoUtils.getSimSerialNumber(context);
//            if (StringUtils.hasText(serial)) {
//                deviceId.append("sn");
//                deviceId.append(serial);
//                return deviceId.toString();
//            }
            // 如果上面都没有， 则生成一个id：随机码
            String id = SystemUtils.getAndroidId(context);
            if (StringUtils.hasText(id)) {
                deviceId.append("id");
                deviceId.append(id);
                return deviceId.toString();
            }
        } catch (Exception e) {
            LogUtil.e(e);
            deviceId.append("uuid").append(getUUID(context));
        }
        return deviceId.toString();
    }

//    public static String getImei(Context context) {
//        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        return tm.getSimSerialNumber();
//    }

    /**
     * 得到全局唯一UUID
     */
    public static String getUUID(Context context) {
        return UUID.randomUUID().toString();
    }


    public static String getMetaData(Context context, String name) {
        try {
            ApplicationInfo appInfo = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA);
            return appInfo.metaData.getString(name);
        } catch (Exception ex) {
            LogUtil.e(ex);
            return null;
        }
    }
}
