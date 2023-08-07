package com.openapi.ks.myapp.task;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PathUtil;
import com.openapi.commons.yewu.net.HttpUtils;

import java.io.File;

/**
 * 图片下载
 * 
 */
public class SaveTextTask extends AsyncTask<String,Void,Void>{
    private String url;
    private Context context;
    private File currentFile;
    public static void startSave(Context context, String url){
        new SaveTextTask(context,url).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    public SaveTextTask(Context context, String url) {
        this.url = url;
        this.context = context;
    }
    @Override
    protected Void doInBackground(String... params) {
        try {
            Uri uri = Uri.parse(url);
            String filename = PathUtil.getFileExtensionFromUrl(url);
            File file = new File( FileUtils.getWriteFilePath(context),filename);
            FileUtils.deleteFile(file);
            HttpUtils.download(context,url,file,null);
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return null;
    }
}