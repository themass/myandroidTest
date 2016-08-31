package com.timeline.vpn.common.util;
/**
 * Created by sssong on 13-12-4.
 */

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;


public class PackageUtils {

    /**
     * 检测包是否安装
     *
     * @param ct
     * @param packName
     * @return
     */
    public static boolean isPackageInstalled(Context ct, String packName) {
        return getAppInfo(ct, packName) != null;
    }

    /**
     * 安装apk包
     *
     * @param context
     * @param apkfile
     */
    public static void installApk(Context context, File apkfile) {
        if (apkfile != null && apkfile.exists()) {
            // 通过Intent安装APK文件
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(apkfile),
                        "application/vnd.android.package-archive");
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 返回应用版本号
     *
     * @param context
     * @return
     */
    public static String getAppVersion(Context context) {
        return getAppVersion(context, context.getPackageName());
    }

    /**
     * 根据packageName包名的应用获取应用版本名称,如未安装返回null
     *
     * @param context
     * @param packageName
     * @return
     */
    public static String getAppVersion(Context context, String packageName) {
        PackageInfo info = getAppInfo(context, packageName);
        if (info != null) {
            return info.versionName;
        }
        return null;
    }

    /**
     * 返回应用version code
     *
     * @param context
     * @return
     */
    public static int getAppVersionCode(Context context) {
        return getAppVersionCode(context, context.getPackageName());
    }

    /**
     * 获取version code
     *
     * @param context
     * @param packageName
     * @return
     */
    public static int getAppVersionCode(Context context, String packageName) {
        PackageInfo info = getAppInfo(context, packageName);
        if (info != null) {
            return info.versionCode;
        }
        return 0;
    }

    /**
     * 获取packageInfo信息
     *
     * @param context
     * @param packageName
     * @return
     */
    public static PackageInfo getAppInfo(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

//    public static getPackageName getAppInfo(Context context) {
//      
//        try {
//            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
//            return info;
//        } catch (NameNotFoundException e) {
//            return null;
//        }
//    }

}
