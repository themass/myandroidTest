package com.openapi.commons.common.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

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
            // wifi mac地址
            String wifiMac = DeviceInfoUtils.getMac(context);
            if (StringUtils.hasText(wifiMac)) {
                deviceId.append("wifi");
                deviceId.append(wifiMac);
                return deviceId.toString();
            }
            // IMEI（imei）
            String dev = DeviceInfoUtils.getImei(context);
            if (StringUtils.hasText(dev)) {
                deviceId.append("imei");
                deviceId.append(dev);
                return deviceId.toString();
            }
            // 序列号（sn）
            String serial = DeviceInfoUtils.getSimSerialNumber(context);
            if (StringUtils.hasText(serial)) {
                deviceId.append("sn");
                deviceId.append(serial);
                return deviceId.toString();
            }
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

    public static String getImei(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getSimSerialNumber();
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
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi.getConnectionInfo();
        String addr = info.getMacAddress();
        if (StringUtils.hasText(addr) && addr.equals("02:00:00:00:00:00")) {
            addr = getNewMacAddr();
        }
        return addr;
    }

    public static String getNewMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;
                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }
                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }
                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * 获取设备名称.
     *
     * @return
     */
    public static String getBuildModel() {
        return Build.MODEL;
    }

    /**
     * 得到全局唯一UUID
     */
    public static String getUUID(Context context) {
        return UUID.randomUUID().toString();
    }

    /**
     * 从apk中获取版本信息
     */
    public static String getChannelFromApk(Context context, String channelKey) {
        //从apk包中获取
        ApplicationInfo appinfo = context.getApplicationInfo();
        String sourceDir = appinfo.sourceDir;
        //默认放在meta-inf/里， 所以需要再拼接一下
        String key = "META-INF/" + channelKey;
        String ret = "";
        ZipFile zipfile = null;
        try {
            zipfile = new ZipFile(sourceDir);
            Enumeration<?> entries = zipfile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = ((ZipEntry) entries.nextElement());
                String entryName = entry.getName();
                if (entryName.startsWith(key)) {
                    ret = entryName;
                    break;
                }
            }
        } catch (IOException e) {
            LogUtil.e(e);
        } finally {
            if (zipfile != null) {
                try {
                    zipfile.close();
                } catch (IOException e) {
                    LogUtil.e(e);
                }
            }
        }
        String[] split = ret.split("_");
        String channel = "";
        if (split.length >= 2) {
            channel = ret.substring(split[0].length() + 1);
        }
        return channel;
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
