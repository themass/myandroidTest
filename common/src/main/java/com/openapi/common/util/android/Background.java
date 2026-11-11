package com.openapi.common.util.android;

import com.openapi.common.util.log.LogUtil;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 替代已弃用的 AsyncTask：后台执行，必要时回到主线程。
 *
 * <ul>
 *   <li>{@link #parallel(Runnable)}：多个任务可以同时跑（对应 AsyncTask.THREAD_POOL_EXECUTOR）。</li>
 *   <li>{@link #serial(Runnable)}：按提交顺序一个接一个跑（对应 AsyncTask.execute 的默认行为）。</li>
 *   <li>{@link #main(Runnable)}：切到主线程。</li>
 * </ul>
 * 任务里抛出的异常会被记录日志，不会让线程池里的线程挂掉。
 */
public final class Background {

    private static final ExecutorService PARALLEL = Executors.newCachedThreadPool();
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Background() {
    }

    public static void parallel(Runnable task) {
        PARALLEL.execute(guarded(task));
    }

    public static void serial(Runnable task) {
        SERIAL.execute(guarded(task));
    }

    public static void main(Runnable task) {
        MAIN.post(task);
    }

    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                LogUtil.e(e);
            }
        };
    }
}
