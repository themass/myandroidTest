package com.openapi.ks.myapp.task;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.qq.e.comm.util.StringUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PathUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
/**
 * 图片下载
 * 
 */
public class SaveImageTask extends AsyncTask<String,Void,Void>{
    private String url;
    private Context context;
    private SaveImageCallBack callBack;
    private File currentFile;
    public static void startSave(Context context, String url, SaveImageCallBack callBack){
        new SaveImageTask(context,url,callBack).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    public SaveImageTask(Context context, String url, SaveImageCallBack callBack) {
        this.url = url;
        this.callBack = callBack;
        this.context = context;
    }

    @Override
    protected Void doInBackground(String... params) {
        Bitmap bitmap = null;
        try {
            bitmap = Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get();
            if (bitmap != null){
                // 在这里执行图片保存方法
                saveImageToGallery(context,url,bitmap);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (currentFile!=null && currentFile.exists()) {
            callBack.onSuccess();
        } else {
            callBack.onFailed();
        }
    }

    public void saveImageToGallery(Context context, String url, Bitmap bmp) {
        // 首先保存图片
        File appDir =PathUtil.initPicturesDiskCacheFile();
        String name = PathUtil.getFileExtensionFromUrl(url);
        LogUtil.i(name);
        if(StringUtil.isEmpty(name)){
            name = System.currentTimeMillis()+"";
        }
        currentFile = new File(appDir, name);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(currentFile);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
        } catch (FileNotFoundException e) {
            LogUtil.e(e);
        } catch (IOException e) {
            LogUtil.e(e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                LogUtil.e(e);
            }
        }

        // 其次把文件插入到系统图库
        try {
            MediaStore.Images.Media.insertImage(context.getContentResolver(),
                    currentFile.getAbsolutePath(), name, null);
        } catch (FileNotFoundException e) {
            LogUtil.e(e);
        }

        // 最后通知图库更新
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(new File(currentFile.getPath()))));
    }
}