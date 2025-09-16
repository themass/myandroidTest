package com.openai.commonutil;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by froger_mcs on 05.11.14.
 */
@SuppressWarnings("deprecation")
public class Utils {
    private static int screenWidth = 0;
    private static int screenHeight = 0;

    // 已移除未使用的dpToPx方法

    public static int getScreenHeight(Context c) {
        if (screenHeight == 0) {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            // 使用现代API (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                screenHeight = wm.getCurrentWindowMetrics().getBounds().height();
            } else {
                // 兼容旧版本
                Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                screenHeight = size.y;
            }
        }

        return screenHeight;
    }

    public static int getScreenWidth(Context c) {
        if (screenWidth == 0) {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            // 使用现代API (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                screenWidth = wm.getCurrentWindowMetrics().getBounds().width();
            } else {
                // 兼容旧版本
                Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                screenWidth = size.x;
            }
        }

        return screenWidth;
    }

    // 已移除未使用的isAndroid5方法

    // 已移除未使用的UrlUtil内部类

}
