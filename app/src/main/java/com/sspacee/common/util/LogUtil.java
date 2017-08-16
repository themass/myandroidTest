package com.sspacee.common.util;

import android.util.Log;

import com.sspacee.common.CommonConstants;

/**
 * Created by themass on 2016/3/2.
 */
public class LogUtil {
    private static final String TAG = "myTag";

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    public static void e(String msg, Throwable t) {
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(msg, t));
        } else {
            Log.e(TAG, msg, t);
        }
    }

    public static void error(String msg, Throwable t) {
        Log.e(TAG, msg, t);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void i(String msg) {
        if (msg == null) {
            Log.i(TAG, "null");
        } else {
            Log.i(TAG, msg);
        }
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void e(Throwable t) {
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(t));
        } else {
            Log.e(TAG, "", t);
        }
    }

    public static void e(String t) {
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(t));
        } else {
            Log.e(TAG, t);
        }
    }
    public static class LogAddEvent {
        public Object data;
        public Object msg;

        public LogAddEvent(Object content) {
            this.data = content;
        }

        public LogAddEvent(String msg, Object content) {
            this.data = content;
            this.msg = msg;
        }
    }
}
