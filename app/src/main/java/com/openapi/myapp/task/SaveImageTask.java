package com.openapi.myapp.task;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PathUtil;
import com.openapi.common.util.StringUtils;

import java.io.File;
import java.io.IOException;

/**
 * 图片下载
 * 
 */
public class SaveImageTask extends AsyncTask<String,Void,Void>{
    private String url;
    private Context context;
    private SaveImageCallBack callBack;
    /** 是否真的写进了系统相册。原来看 currentFile.exists()，文件没写进去时也可能误判。 */
    private boolean saved;
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
        if (saved) {
            callBack.onSuccess();
        } else {
            callBack.onFailed();
        }
    }

    /**
     * 保存到系统相册。
     * <p>
     * 原来先用 File API 写到 {@code Pictures/} 再 insertImage + 扫描广播。targetSdk 37 下：
     * Android 10 不能用路径直接在公共目录建文件（写失败，但 onPostExecute 只看文件在不在）；
     * Android 9 需要运行时授予 WRITE_EXTERNAL_STORAGE，而我们已不在启动时申请它；
     * ACTION_MEDIA_SCANNER_SCAN_FILE 也早已废弃。
     * <p>
     * 现在：Android 10+ 走 MediaStore（RELATIVE_PATH + IS_PENDING），不需要任何权限；
     * Android 9 及以下仍需存储权限，由调用方在点击保存时按需申请（{@link #needsLegacyPermission}）。
     */
    public void saveImageToGallery(Context context, String url, Bitmap bmp) {
        String name = PathUtil.getFileExtensionFromUrl(url);
        LogUtil.i(name);
        if (StringUtils.isEmpty(name)) {
            name = System.currentTimeMillis() + ".jpg";
        } else if (!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpe?g|png|webp)$")) {
            name = name + ".jpg";
        }
        android.content.ContentResolver resolver = context.getContentResolver();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + File.separator + PathUtil.FILEPATH);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return;
                }
                try (java.io.OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null || !bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw new IOException("写入相册失败");
                    }
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            } else {
                // Android 9：插入后由 MediaStore 自己落盘，同样不再手工拼公共目录路径
                String inserted = MediaStore.Images.Media.insertImage(resolver, bmp, name, null);
                if (inserted == null) {
                    return;
                }
            }
            saved = true;
        } catch (Exception e) {
            LogUtil.e(e);
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null);   // 半截文件不要留在相册里
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Android 9 及以下保存到相册需要运行时存储权限；10+ 不需要。 */
    public static boolean needsLegacyPermission(Context context) {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                && androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}