package com.openapi.ks.myapp.task;

import android.content.Context;

import com.openapi.core.util.LogUtil;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片删除任务
 */
public class DelImageTask {
    private final String path;
    private final Context context;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static void startDel(Context context, String path) {
        new DelImageTask(context, path).execute();
    }
    
    public DelImageTask(Context context, String path) {
        this.path = path;
        this.context = context;
    }
    
    public void execute() {
        executor.execute(() -> {
            try {
                File file = new File(path);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    LogUtil.i("删除图片：" + path + ", 结果：" + deleted);
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        });
    }
}