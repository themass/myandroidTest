package com.timeline.vpn.common.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.DisplayMetrics;


/**
 * @Author: Cyning Li
 * <p/>
 * 和手机屏幕相关的分辨率和dp px sp之间转换的工具
 */
public class DisplayUtil {

    private static DisplayMetrics dm = null;

    static public DisplayMetrics getDisplayMetrics(Context context) {
        if (dm == null) {
            dm = context.getResources().getDisplayMetrics();
            // L.i(dm.toString());
        }
        return dm;
    }

    static public int px2dp(Context context, int px) {
        if (context == null) {
            throw new NullPointerException("the context is null");
        }
        getDisplayMetrics(context);
        final float density = dm.density;
        return (int) (px / density + 0.5f);
    }

    static public int dp2px(Context context, float dp) {
        if (context == null) {
            throw new NullPointerException("the context is null");
        }
        getDisplayMetrics(context);
        final float density = dm.density;
        return (int) (dp * density + 0.5f);
    }

    static public int px2sp(Context context, float px) {
        if (context == null) {
            throw new NullPointerException("the context is null");
        }
        getDisplayMetrics(context);
        return (int) (px / dm.scaledDensity + 0.5f);
    }

    static public int sp2px(Context context, float sp) {
        if (context == null) {
            throw new NullPointerException("the context is null");
        }
        getDisplayMetrics(context);
        return (int) (sp * dm.scaledDensity + 0.5f);
    }

    static public int getScreenWidthInPx(Context context) {
        getDisplayMetrics(context);
        return dm.widthPixels;
    }

    static public int getScreenHeightInPx(Context context) {
        getDisplayMetrics(context);
        return dm.heightPixels;
    }

    static public int getScreenWidthInDp(Context context) {
        getDisplayMetrics(context);
        return (int) ((float) dm.widthPixels * (160 / dm.xdpi));
    }

    static public int getScreenHeightInDp(Context context) {
        getDisplayMetrics(context);
        int screenHeight = dm.heightPixels;
        return (int) ((float) screenHeight / dm.density);
    }

    static public float getDensity(Context context) {
        getDisplayMetrics(context);
        return dm.density;
    }

    public static int getScreenTitleBarHeight(Context context) {
        Rect rect = new Rect();
        ((Activity) context).getWindow().getDecorView()
                .getWindowVisibleDisplayFrame(rect);
        return rect.top;
    }

    /**
     * 获得设备html宽度
     *
     * @param context
     * @return
     */
    public static int getDeviceHtmlWidth(Context context) {

        if (DeviceInfoUtils.isHoneycombTablet(context) && isLandscape(context)) {
            return getTabletWebViewWidth(context);
        }

        int width = (int) (getScreenWidthInPx(context) / context.getResources().getDisplayMetrics().density);

        return width;
    }

    /**
     * 当前是否横屏
     *
     * @param context
     * @return
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 获取平板在横屏时webview的宽度
     *
     * @param c
     * @return
     */
    public static int getTabletWebViewWidth(Context c) {
        // 0.82f根据当前webview的padding计算得来
        return (int) ((float) DisplayUtil.getScreenHeightInPx(c) * 0.82f / c.getResources().getDisplayMetrics().density);
    }


    /**
     * <li><p>getDimension()是基于当前DisplayMetrics进行转换，获取指定资源id对应的尺寸。
     * 文档里并没说这里返回的就是像素，要注意这个函数的返回值是float，像素肯定是int。
     * </p></li>
     * <li>getDimensionPixelSize()与getDimension()功能类似，不同的是将结果转换为int，并且小数部分四舍五入。</li>
     * <li>getDimensionPixelOffset()与getDimension()功能类似，不同的是将结果转换为int，并且偏移转换（offset conversion，
     * 函数命名中的offset是这个意思）是直接截断小数位，即取整（其实就是把float强制转化为int，注意不是四舍五入哦）。
     */
    public static int getDimension(Context context, int dimenId) {
        return (int) (context.getResources().getDimension(dimenId) + 0.5);
    }
}
