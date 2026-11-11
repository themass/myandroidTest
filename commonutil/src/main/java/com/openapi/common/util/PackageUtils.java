package com.openapi.common.util;
/**
 * Created by sssong on 13-12-4.
 */

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.openapi.common.util.CollectionUtils;
import com.openapi.common.util.StringUtils;

import java.io.File;
import java.util.List;

@SuppressWarnings("deprecation")
public class PackageUtils {



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
            // 兼容性处理：API 28+ 使用新方法，否则使用旧方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            } else {
                int versionCode = info.versionCode;
                return versionCode;
            }
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

    public static boolean hasBrowser(Context context) {
        String default_browser = "android.intent.category.DEFAULT";
        String browsable = "android.intent.category.BROWSABLE";
        String view = "android.intent.action.VIEW";
        Intent intent = new Intent(view);
        intent.addCategory(default_browser);
        intent.addCategory(browsable);
        Uri uri = Uri.parse("http://");
        intent.setDataAndType(uri, null);
        List<ResolveInfo> resolveInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !CollectionUtils.isEmpty(resolveInfoList);
    }

    public static boolean isApk(String str) {
        return StringUtils.hasText(str) && str.endsWith(".apk") ? true : false;
    }
}
