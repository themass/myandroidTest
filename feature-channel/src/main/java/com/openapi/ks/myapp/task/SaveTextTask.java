package com.openapi.ks.myapp.task;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PathUtil;
import com.openapi.common.net.HttpUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片下载
 */
public class SaveTextTask {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private String url;
    private Context context;
    private File currentFile;

    public static void startSave(Context context, String url) {
        new SaveTextTask(context, url).execute();
    }

    public SaveTextTask(Context context, String url) {
        this.url = url;
        this.context = context;
    }

    public void execute() {
        executor.execute(() -> {
            try {
                Uri uri = Uri.parse(url);
                String filename = PathUtil.getFileExtensionFromUrl(url);
                File file = new File(FileUtils.getWriteFilePath(context), filename);
                FileUtils.deleteFile(file);
                HttpUtils.download(context, url, file, null);
            } catch (Exception e) {
                LogUtil.e(e);
            }
        });
    }
}