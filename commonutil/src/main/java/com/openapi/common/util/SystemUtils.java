package com.openapi.common.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;


import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import static android.content.Context.CLIPBOARD_SERVICE;
@SuppressWarnings("deprecation")
public class SystemUtils {

    /** 中文语言代码（Locale.getLanguage() 的返回值）。 */
    public static final String LANG_ZH = "zh";





    public static String getAndroidId(Context context) {
        return android.provider.Settings.Secure.ANDROID_ID;
    }

    public static boolean isApkDebugable(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception ignored) {

        }
        return false;
    }


    public static String getLang(Context context) {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            // 兼容旧版本，使用现代方式获取locale
            locale = context.getResources().getConfiguration().getLocales().get(0);
        }
        LogUtil.i("getLang", locale.getLanguage());
        return locale.getLanguage();
    }

    public static String getCpuType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return "" + Arrays.asList(Build.SUPPORTED_ABIS);
        } else {
            // 兼容旧版本，使用SUPPORTED_ABIS的替代方案
            return Build.SUPPORTED_ABIS[0];
        }
    }

    public static void copy(Context context, String text) {
        ClipboardManager myClipboard;
        myClipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        ClipData myClip;
        myClip = ClipData.newPlainText("text", text);
        myClipboard.setPrimaryClip(myClip);
    }
}
