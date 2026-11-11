package com.openapi.common.util.android;

import com.openapi.common.util.log.LogUtil;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtils {
    private final static int BUFFER_SIZE = 1024;
    public static String CACHDIR = "ADeepseek";


    /**
     * 递归删除文件
     *
     * @param file 要删除的文件
     */
    public static boolean deleteFile(File file) {
        if (file != null && file.exists()) {
            try {
                if (file.isFile()) { // 是文件直接删除
                    file.delete();
                } else if (file.isDirectory()) { // 是文件夹的话递归删除文件夹下的文件
                    File files[] = file.listFiles();
                    for (File f : files) {
                        deleteFile(f);
                    }
                }
                file.delete();
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    public static String readAssets(Context context, String fileName) {
        BufferedReader reader = null;
        try {
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(fileName), "UTF-8"));

            // do reading, usually loop until end of file reading
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                sb.append(mLine).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            //log the exception
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //log the exception
                }
            }
        }
        return "";
    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getWriteFilePath(Context context) {
        if (context == null) {
            LogUtil.e("传入的 Context 为 null，无法获取写入路径");
            return null;
        }

        // 尝试获取 Android 10 及以上版本的外部存储路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir != null) {
                if (externalFilesDir.canWrite()) {
                    LogUtil.i("使用 Context.getExternalFilesDir 获取路径，路径为: " + externalFilesDir.getAbsolutePath());
                    return externalFilesDir.getPath() + File.separator + CACHDIR;
                } else {
                    LogUtil.w("Context.getExternalFilesDir 获取的路径不可写，路径为: " + externalFilesDir.getAbsolutePath());
                }
            }
        }
        // 尝试获取 Android 10 以下版本的外部存储路径
        else {
            String sdStatus = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(sdStatus)) {
                File rootFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (rootFile != null) {
                    if (rootFile.canWrite()) {
                        LogUtil.i("sdStatus:" + sdStatus + "; filepath:" + rootFile.getAbsolutePath() + "; path:" + rootFile.getPath());
                        return rootFile.getPath() + File.separator + CACHDIR;
                    } else {
                        LogUtil.w("外部存储公共下载目录不可写，路径为: " + rootFile.getAbsolutePath());
                    }
                }
            }
        }

        // 尝试获取外部缓存目录
        File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) {
            if (externalCacheDir.canWrite()) {
                LogUtil.i("使用 getExternalCacheDir 获取路径，路径为: " + externalCacheDir.getAbsolutePath());
                return externalCacheDir.getPath() + File.separator + CACHDIR;
            } else {
                LogUtil.w("外部缓存目录不可写，路径为: " + externalCacheDir.getAbsolutePath());
            }
        }

        // 尝试获取内部缓存目录
        File internalCacheDir = context.getCacheDir();
        if (internalCacheDir != null) {
            if (internalCacheDir.canWrite()) {
                LogUtil.i("使用 getCacheDir 获取路径，路径为: " + internalCacheDir.getAbsolutePath());
                return internalCacheDir.getPath() + File.separator + CACHDIR;
            } else {
                LogUtil.w("内部缓存目录不可写，路径为: " + internalCacheDir.getAbsolutePath());
            }
        }

        LogUtil.e("无法获取合适的存储路径");
        return null;
    }

    public static boolean ensureFile(Context context, String filePath) {
        File path = new File(filePath);
        if (!path.exists()) {
            LogUtil.d("Create the path:" + filePath);
            path.mkdirs();
        }
        return true;
    }
}
