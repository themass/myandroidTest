package com.sspacee.common.util;

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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.sspacee.common.CommonConstants;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import static android.content.Context.CLIPBOARD_SERVICE;

public class SystemUtils {
    /**
     * 获取一个自定义风格的Dialog
     *
     * @param activity   上下文对象
     * @param style      风格
     * @param customView 自定义view
     * @return dialog
     */
    public static Dialog getCustomeDialog(Activity activity, int style,
                                          View customView) {
        Dialog dialog = new Dialog(activity, style);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(customView);
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = LayoutParams.MATCH_PARENT;
        lp.height = LayoutParams.MATCH_PARENT;
        lp.x = 0;
        lp.y = 0;
        window.setAttributes(lp);
        return dialog;
    }

    public static Dialog getCustomeDialog(Activity activity, int style,
                                          int customView) {
        Dialog dialog = new Dialog(activity, style);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(customView);
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = LayoutParams.MATCH_PARENT;
        lp.height = LayoutParams.MATCH_PARENT;
        lp.x = 0;
        lp.y = 0;
        window.setAttributes(lp);
        return dialog;
    }

    /**
     * 获取手机屏幕高度
     *
     * @param context
     * @return
     */
    public static int getDisplayHeight(Context context) {
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        // 获取屏幕信息
        wm.getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels;
    }

    /**
     * 获取手机屏幕宽度
     *
     * @param context
     * @return
     */
    public static int getDisplayWidth(Context context) {
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        // 获取屏幕信息
        wm.getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }

    /**
     * 反射方法获取状态栏高度
     *
     * @return
     */
    public static int getStatusBarHeight(Context context) {
        int statusBarHeight = 20;
        try {
            Class<?> _class = Class.forName("com.android.internal.R$dimen");
            Object object = _class.newInstance();
            Field field = _class.getField("status_bar_height");
            int restult = Integer.parseInt(field.get(object).toString());
            statusBarHeight = context.getResources().getDimensionPixelSize(
                    restult);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Toast.makeText(getActivity(), "StatusBarHeight = " + statusBarHeight,
        // Toast.LENGTH_SHORT).show();
        return statusBarHeight;
    }

    /**
     * 以最省内存的方式读取本地资源的图片
     *
     * @param context
     * @param resId
     * @return
     */
    public static Bitmap readBitMap(Context context, int resId) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
//		opt.inPreferredConfig = Bitmap.Config.RGB_565;
//		opt.inPurgeable = true;
//		opt.inInputShareable = true;
        opt.inSampleSize = 2;
        // 获取资源图片
        InputStream is = context.getResources().openRawResource(resId);
        return BitmapFactory.decodeStream(is, null, opt);
    }

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

    public static boolean isZH(Context context) {
        return CommonConstants.LANG_ZH.equals(getLang(context));
    }

    public static String getLang(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        if (language.contains("zh"))
            return CommonConstants.LANG_ZH;
        else
            return CommonConstants.LANG_US;
    }

    public static String getCpuType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return "" + Arrays.asList(Build.SUPPORTED_ABIS);
        } else {
            return Build.CPU_ABI;
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
