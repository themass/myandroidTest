package com.sspacee.common.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by froger_mcs on 05.11.14.
 */
public class Utils {
    private static int screenWidth = 0;
    private static int screenHeight = 0;

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static int getScreenHeight(Context c) {
        if (screenHeight == 0) {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            screenHeight = size.y;
        }

        return screenHeight;
    }

    public static int getScreenWidth(Context c) {
        if (screenWidth == 0) {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            screenWidth = size.x;
        }

        return screenWidth;
    }

    public static boolean isAndroid5() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     */
    public static class UrlUtil {
        public static String encode(String param) {
            if (param == null) {
                return "";
            }
            try {
                return URLEncoder.encode(param, "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return "";
        }
    }
    public static final void hideSoftInput(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0); //强制隐藏键盘
    }
}
