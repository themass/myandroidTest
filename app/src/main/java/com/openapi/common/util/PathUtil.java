package com.openapi.common.util;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * 里面存放的是关于路径的一些helper类
 *
 * @author Cyning
 * @date 2014-7-10 上午9:57:12
 */
public class PathUtil {
    public static final String BROWSER_SCHEMA = "browser://config?url=%s";
    private static final String FILEPATH = "AFreeVPN";

    public static File getFileDiskCacheDir(Context context, String fileName) throws IOException {
        return getFileDiskCacheDir(context, fileName, true);
    }

    public static File getFileDiskCacheDir(Context context, String fileName, boolean isCreate) throws IOException {
        File folder = getDiskCacheDir(context, FILEPATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, fileName);
        if (!file.exists() && isCreate) {
            file.createNewFile();
        }
        return file;
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context    The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     * @return The cache dir
     */
    public synchronized static File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable() ?
                        getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * @param context
     * @param uniqueName
     * @return 获取内部存储。/data/data 下的
     */
    public synchronized static File getInterCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        return new File(context.getCacheDir().getPath() + File.separator + uniqueName);
    }


    /**
     * Check if external storage is built-in or removable.
     *
     * @return True if external storage is removable (like an SD card), false
     * otherwise.
     */
    public static boolean isExternalStorageRemovable() {
        return Environment.isExternalStorageRemovable();
    }

    public static File getExternalCacheDir(Context context) {
        return context.getExternalCacheDir() == null ? getExCacheFile(context) : context.getExternalCacheDir();
    }

    private static File getExCacheFile(Context context) {
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }
    public static File initPicturesDiskCacheFile(){
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsoluteFile();//注意小米手机必须这样获得public绝对路径
        File appDir = new File(file ,FILEPATH);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return appDir;
    }
    public static String getLocalOpenUrl(String url) {
        try {
            String ecode = URLEncoder.encode(url, "utf-8");
            return String.format(BROWSER_SCHEMA, ecode);
        } catch (Exception e) {
            LogUtil.e(e);
            return null;
        }
    }
    public static String getFileExtensionFromUrl(String url) {
        if (!TextUtils.isEmpty(url)) {
            int fragment = url.lastIndexOf('#');
            if (fragment > 0) {
                url = url.substring(0, fragment);
            }

            int query = url.lastIndexOf('?');
            if (query > 0) {
                url = url.substring(0, query);
            }

            int filenamePos = url.lastIndexOf('/');
            String filename =
                    0 <= filenamePos ? url.substring(filenamePos + 1) : url;
            return filename;
        }

        return null;
    }
}
