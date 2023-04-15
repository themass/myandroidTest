package com.openapi.common.util;

import android.text.Html;
import android.widget.Toast;

import com.openapi.myapp.base.MyApplication;

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
    public static void showRedLong(String msg) {
        String m_ToastStr = "<font color='#EE0000'>"+msg+"</font>";
        Toast.makeText(MyApplication.getInstance(), Html.fromHtml(m_ToastStr), Toast.LENGTH_LONG).show();
    }
    public static void showShort(int msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLong(int msg) {
        Toast.makeText(MyApplication.getInstance(), msg, Toast.LENGTH_LONG).show();
    }
}
