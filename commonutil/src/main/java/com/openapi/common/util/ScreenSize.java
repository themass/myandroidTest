package com.openapi.common.util;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/**
 * 屏幕尺寸和 dp/px 换算。原来分散在 DensityUtil、DisplayUtil、Utils 三个类里，现在合并到这里。
 */
public final class ScreenSize {

    private static int screenWidth = 0;
    private static int screenHeight = 0;

    private ScreenSize() {
    }

    public static DisplayMetrics metrics(Context context) {
        return context.getResources().getDisplayMetrics();
    }

    public static int dp2px(Context context, float dp) {
        final float scale = metrics(context).density;
        return (int) (dp * scale + 0.5f);
    }

    public static int px2dp(Context context, float px) {
        final float scale = metrics(context).density;
        return (int) (px / scale + 0.5f);
    }

    /** 窗口宽度（API 30+ 用窗口边界，更早的版本用默认显示器尺寸），第一次取值后缓存。 */
    public static int width(Context context) {
        if (screenWidth == 0) {
            screenWidth = windowSize(context).x;
        }
        return screenWidth;
    }

    /** 窗口高度，第一次取值后缓存。 */
    public static int height(Context context) {
        if (screenHeight == 0) {
            screenHeight = windowSize(context).y;
        }
        return screenHeight;
    }

    private static Point windowSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new Point(wm.getCurrentWindowMetrics().getBounds().width(),
                    wm.getCurrentWindowMetrics().getBounds().height());
        }
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static void logDensity(Context context) {
        LogUtil.w("sw =" + px2dp(context, metrics(context).widthPixels) + "dp");
        LogUtil.w("dpi =" + metrics(context).densityDpi);
        LogUtil.w("Metrics=" + metrics(context));
    }
}
