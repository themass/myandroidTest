package com.openapi.common.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.openapi.common.CommonConstants;
import com.openapi.myapp.base.MyApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private static final String TAG = "CrashHandler";
    /** 单文件上限：超过就不再写，避免异常循环把磁盘写满。 */
    private static final long MAX_BYTES = 256 * 1024;
    private static final Object LOCK = new Object();

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
        synchronized (LOCK) {
            Context context = MyApplication.getInstance();
            if (context == null) {
                return;
            }
            File file = new File(FileUtils.getBugFilePath());
            if (file.length() > MAX_BYTES) {
                return;
            }
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            Writer out = null;
            try {
                out = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
                Writer w = out;
                w.write("==== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(new Date()) + " ====\n");
                w.write("thread: " + thread.getName() + "\n");
                w.write("device: " + Build.MANUFACTURER + " " + Build.MODEL
                        + " / Android " + Build.VERSION.RELEASE
                        + " (API " + Build.VERSION.SDK_INT + ")\n");
                try {
                    w.write("app: " + context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0).versionName + "\n");
                } catch (Exception ignored) {
                }
                w.write(Log.getStackTraceString(throwable));
                w.write("\n\n");
            } catch (IOException e) {
                Log.w(TAG, "崩溃信息写入失败: " + e.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
