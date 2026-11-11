package com.openapi.common.util;

import android.os.Build;
import android.util.Log;

/**
 * 崩溃捕获：把未捕获异常写到 {@link CommonConstants#BUG_FILE}，随下次「报告日志」一起上传。
 * <p>
 * 之前没有任何崩溃上报，用户说"连不上"/"闪退"时手上一点信息都没有。跨国 VPN 的失败
 * 场景又特别多（运营商、漫游、双卡、厂商省电策略），没有堆栈基本无法定位。
 * <p>
 * 只记堆栈、机型、系统版本和 App 版本，不写用户标识。落盘后仍然把异常交回系统默认处理
 * （进程照常崩溃），不改变原有行为。
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler previous;

    private CrashHandler(Thread.UncaughtExceptionHandler previous) {
        this.previous = previous;
    }

    /** 在 Application.onCreate 里调用一次。重复调用无副作用。 */
    public static void install() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            write(thread, throwable);
        } catch (Throwable ignored) {
            // 记录失败不能影响原有崩溃流程
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable);
        }
    }

    private static void write(Thread thread, Throwable throwable) {
        // 统一由 DiagnosticLog 落盘（含设备信息与轮转），这里不再维护第二份写入逻辑
        DiagnosticLog.error("CRASH on thread " + thread.getName()
                + " (" + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + ")", throwable);
    }
}
