package com.openapi.common.util;

import android.util.Log;

import com.openapi.common.CommonConstants;

/**
 * Created by dengt on 2016/3/2.
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
        Log.e(TAG, msg, t);
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(msg, t));
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
        // 修复前：非 DEBUG 构建下只把事件丢进 EventBus，而从未有订阅者消费它
        // （logcat 里能看到 "No subscribers registered for event class
        // ...LogAddEvent"）——结果是这个项目的错误级别日志，在任何构建下
        // 都从未真正写进过 logcat，排查问题时后台看起来"什么都没发生"。
        // Log.e() 对 Android 系统日志来说是标准、安全的调用，不存在"调试信息
        // 泄露到生产环境"的顾虑（它不是 System.out，可见范围本就受 adb 调试
        // 权限限制），所以改成无条件打印；EventBus 事件继续保留，万一将来真
        // 有订阅者（比如应用内日志查看页）要用。
        Log.e(TAG, "", t);
        DiagnosticLog.error("", t);
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(t));
        }
    }

    public static void e(String t) {
        Log.e(TAG, t);
        DiagnosticLog.error(t, null);
        if (!CommonConstants.DEBUG) {
            EventBusUtil.getEventBus().post(new LogUtil.LogAddEvent(t));
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
