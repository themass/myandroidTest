package com.sspacee.common.util;

import android.widget.Toast;

import com.timeline.vpn.base.MyApplication;

/**
 * Created by HugoXie on 16/5/23.
 *
 * Email: Hugo3641@gamil.com
 * GitHub: https://github.com/xcc3641
 * Info:
 */
public class ToastUtil {

    public static void showShort(String msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(String msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_LONG).show();
    }
    public static void showShort(int msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(int msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_LONG).show();
    }
}
