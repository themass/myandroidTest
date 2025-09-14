package com.openapi.ks.myapp.task;

import android.content.Context;
import android.os.AsyncTask;

import com.openai.commonutil.LogUtil;

import java.io.File;

/**
 * 图片删除任务
 */
public class DelImageTask extends AsyncTask<String, Void, Void> {
    private final String path;
    private final Context context;

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
            File file = new File(path);
            if (file.exists()) {
                boolean deleted = file.delete();
                LogUtil.i("删除图片：" + path + ", 结果：" + deleted);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return null;
    }
}