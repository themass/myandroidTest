package com.openapi.ks.myapp.task;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.PathUtil;
import com.openapi.core.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片下载
 */
public class SaveImageTask {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private String url;
    private Context context;
    private SaveImageCallBack callBack;
    private File currentFile;

    public static void startSave(Context context, String url, SaveImageCallBack callBack) {
        new SaveImageTask(context, url, callBack).execute();
    }

    public SaveImageTask(Context context, String url, SaveImageCallBack callBack) {
        this.url = url;
        this.callBack = callBack;
        this.context = context;
    }

    public void execute() {
        executor.execute(() -> {
            try {
                Bitmap bitmap = null;
                try {
                    bitmap = Glide.with(context)
                            .asBitmap()
                            .load(url)
                            .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                            .get();
                    if (bitmap != null) {
                        // 在这里执行图片保存方法
                        saveImageToGallery(context, url, bitmap);
                    }
                } catch (Exception e) {
                    LogUtil.e(e);
                }
                
                // 在主线程中执行回调
                mainHandler.post(() -> {
                    if (currentFile != null && currentFile.exists()) {
                        callBack.onSuccess();
                    } else {
                        callBack.onFailed();
                    }
                });
            } catch (Exception e) {
                LogUtil.e(e);
                mainHandler.post(() -> {
                    callBack.onFailed();
                });
            }
        });
    }

    public void saveImageToGallery(Context context, String url, Bitmap bmp) {
        // 首先保存图片
        File appDir = PathUtil.initPicturesDiskCacheFile();
        String name = PathUtil.getFileExtensionFromUrl(url);
        if (StringUtils.isEmpty(name)) {
            name = System.currentTimeMillis() + "";
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