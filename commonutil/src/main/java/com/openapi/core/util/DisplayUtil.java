package com.openapi.core.util;

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


    static public int dp2px(Context context, float dp) {
        if (context == null) {
            throw new NullPointerException("the context is null");
        }
        getDisplayMetrics(context);
        final float density = dm.density;
        return (int) (dp * density + 0.5f);
    }

    static public float getDensity(Context context) {
        getDisplayMetrics(context);
        return dm.density;
    }

}
