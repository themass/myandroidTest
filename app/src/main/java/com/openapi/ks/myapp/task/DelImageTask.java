package com.openapi.ks.myapp.task;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PathUtil;
import com.openai.commonutil.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 图片下载
 */
public class DelImageTask extends AsyncTask<String, Void, Void> {
    private final String path;
    private Context context;

    public static void startDel(Context context, String path) {
        new DelImageTask(context, path).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    public DelImageTask(Context context, String path) {
        this.path = path;
        this.context = context;
    }
    @Override
    protected Void doInBackground(String... params) {
        try {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(() -> {
                LogUtil.i("删除图片："+path);
                new File(path).delete();
            }, 10, TimeUnit.SECONDS); // 延迟3秒

        } catch (Exception e) {
            LogUtil.e(e);
        }
        return null;
    }

}