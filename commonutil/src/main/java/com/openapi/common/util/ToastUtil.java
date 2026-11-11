package com.openapi.common.util;

import android.content.Context;
import android.widget.Toast;
/**
 * Created by HugoXie on 16/5/23.
 * <p>
 * Email: Hugo3641@gamil.com
 * GitHub: https://github.com/xcc3641
 * Info:
 */
public class ToastUtil {
    public static Context mContext;

    public static void showShort(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
    }

    public static void showShort(int msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(int msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
    }
}
