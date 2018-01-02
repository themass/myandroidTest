package com.sspacee.common.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * 单位转换工具类
 *
 * @author wanghl-a
 */
public class DensityUtil {
    /**
     * dip转换px
     *
     * @param context 上下文
     * @param dpValue dip值
     * @return px值
     */
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
    public static int getScreenWidth(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }
    /**
     * px转换dip
     *
     * @param context 上下文
     * @param pxValue px值
     * @return dip值
     */
    public static int px2dip(Context context, float pxValue) {
        final float scale = DisplayUtil.getDensity(context);
        return (int) (pxValue / scale + 0.5f);
    }

    public static float getDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    public static DisplayMetrics getDensityDisplayMetrics(Context context) {
        return context.getResources().getDisplayMetrics();
    }

    public static void logDensity(Context context) {
        LogUtil.w("sw =" + px2dip(context, getDensityDisplayMetrics(context).widthPixels) + "dp");
        LogUtil.w("dpi =" + getDensityDisplayMetrics(context).densityDpi);
        LogUtil.w("Metrics=" + getDensityDisplayMetrics(context));
    }
    public static int dip2sp(Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }
    /**
     * 获取ActionBarSize
     */
    public static int getActionBarSize(Context context) {
        int actionBarSize = 0;
        TypedArray actionbarSizeTypedArray = null;
        try {
            actionbarSizeTypedArray = context.obtainStyledAttributes(new int[]{android.R.attr.actionBarSize});
            actionBarSize = (int) actionbarSizeTypedArray.getDimension(0, 0);
        } finally {
            if (actionbarSizeTypedArray != null)
                actionbarSizeTypedArray.recycle();
        }
        return actionBarSize;
    }
}
