package com.openapi.core.view.view.gallery;

import android.content.Context;
import android.graphics.PointF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/**
 * Created by shuyu on 2016/11/9.
 */
@SuppressWarnings("deprecation")
public class CommonUtil {

    public static PointF getNeedSize(Context context, final float width, final float height) {
        float imgHeight;
        float imgWidth;
        if (height > width) {
            float screenScale = (float) getScreenHeight(context) / getScreenWidth(context);
            float imtScale = height / width;
            if (screenScale < imtScale) {
                imgHeight = getScreenHeight(context);
                imgWidth = getScreenHeight(context) * width / height;
            } else {
                imgHeight = getScreenWidth(context) * height / width;
                imgWidth = getScreenWidth(context);
            }
        } else {
            imgHeight = getScreenWidth(context) * height / width;
            imgWidth = getScreenWidth(context);
        }
        return new PointF(imgWidth, imgHeight);
    }

    public static int getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        // 使用现代API (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.getCurrentWindowMetrics().getBounds().width();
        } else {
            // 兼容旧版本，使用DisplayMetrics
            DisplayMetrics dm = new DisplayMetrics();
            @SuppressWarnings("deprecation")
            Display display = wm.getDefaultDisplay();
            display.getMetrics(dm);
            return dm.widthPixels;
        }
    }

    public static int getScreenHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        // 使用现代API (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.getCurrentWindowMetrics().getBounds().height();
        } else {
            // 兼容旧版本，使用DisplayMetrics
            DisplayMetrics dm = new DisplayMetrics();
            @SuppressWarnings("deprecation")
            Display display = wm.getDefaultDisplay();
            display.getMetrics(dm);
            return dm.heightPixels;
        }
    }


    public static float dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return dipValue * scale + 0.5f;
    }

    public static int sp2px(Context context, float spValue) {
        // 使用现代方式计算sp到px的转换
        return (int) (spValue * context.getResources().getDisplayMetrics().density + 0.5f);
    }


}
