package com.openai.commonutil;

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

import com.openai.CommonConstants;

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
    public static String VOLLEY_PATH = "volley";
    public static String CACHDIR = "ADeepseek";
    /**
     * 解压文件
     *
     * @param sourceZip
     * @param outFileName
     * @return
     * @throws IOException
     */
    public static boolean unZipFile(String sourceZip, String outFileName) {
        OutputStream os = null;
        InputStream is = null;
        try {
            ZipFile zfile = new ZipFile(sourceZip);
            Enumeration zList = zfile.entries();
            ZipEntry ze;
            byte[] buf = new byte[BUFFER_SIZE];
            int readLen;
            while (zList.hasMoreElements()) {
                // 从ZipFile中得到一个ZipEntry
                ze = (ZipEntry) zList.nextElement();
                if (ze.isDirectory()) {
                    continue;
                }
                // 以ZipEntry为参数得到一个InputStream，并写到OutputStream中
                os = new BufferedOutputStream(
                        new FileOutputStream(getRealFileName(outFileName,
                                ze.getName())));
                is = new BufferedInputStream(
                        zfile.getInputStream(ze));
                while ((readLen = is.read(buf, 0, BUFFER_SIZE)) != -1) {
                    os.write(buf, 0, readLen);
                }
                is.close();
                os.close();
            }
            zfile.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {

                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {

                }
            }
        }
        return true;

    }


    /**
     * 给定根目录，返回一个相对路径所对应的实际文件名.
     *
     * @param baseDir     指定根目录
     * @param absFileName 相对路径名，来自于ZipEntry中的name
     * @return java.io.File 实际的文件
     */
    public static File getRealFileName(String baseDir, String absFileName) {
        String[] dirs = absFileName.split("/");

        File ret = new File(baseDir);

        if (dirs.length > 1) {
            for (int i = 0; i < dirs.length - 1; i++) {
                ret = new File(ret, dirs[i]);
            }
        }
        if (!ret.exists()) {
            ret.mkdirs();
        }
        ret = new File(ret, dirs[dirs.length - 1]);
        return ret;
    }
    public static byte[] readFile(String file){
        byte[] data = new byte[0];
        try (FileInputStream fis = new FileInputStream(file)) {
            data = new byte[fis.available()]; // 创建一个足够大的字节数组
            fis.read(data); // 读取文件内容到字节数组
        } catch (IOException e) {
           LogUtil.e(e); // 打印异常信息
        }
        return data;
    }
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

    public static String readFileContent(Uri uri, Context context) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                // 使用 InputStreamReader 和 StringWriter 读取字符串
                InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                StringWriter writer = new StringWriter();
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, length);
                }
                return  writer.toString();

            }
        } catch (IOException e) {
            LogUtil.e(e);
        }
        return null;
    }
    public static String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    public static FileInfo getFileFromUri(Uri uri, Context context) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        // 创建临时文件
        String timeStamp = System.currentTimeMillis()+"";
        String orgName = getFileNameFromUri(context, uri);
        String name = "UPLOAD_" +timeStamp+"_"+ orgName;
        LogUtil.i("fileName="+name);
        long size = 0;
        File tempFile = new File(FileUtils.getWriteFilePath(context), name);
        try (InputStream is = resolver.openInputStream(uri);
             OutputStream os = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                size+=bytesRead;
            }
        }
        FileInfo info = new FileInfo();
        info.file = tempFile;
        info.fileName = orgName;
        info.fileSize = formatFileSize(size);
        return info;
    }
    public static String getMimeTypeFromExtension(Uri uri) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return null;
    }
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            mimeType = context.getContentResolver().getType(uri);
        }
        if (mimeType == null) {
            mimeType = getMimeTypeFromExtension(uri);
        }
        LogUtil.i("mimeType="+mimeType);
        return mimeType;
    }
    public static String getExtensionFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }
    public static class FileInfo{
        public File file;
        public String fileName;
        public String fileSize;
    }

    // 使用系统工具打开 uri

    public static void openUriWithSystem(Activity activity, Uri uri) {
        String mimeType = null;
        try {
            mimeType = activity.getContentResolver().getType(uri); //尝试使用 contentResolver
        } catch (Exception e){
            // contentResolver 获取meteType失败
            LogUtil.e("getContentResolver().getType failure ",e);
        }
        if (mimeType == null) {
            mimeType = getMimeTypeFromUri(activity,uri); //尝试 通过后缀名称来获取 mimetype.
        }

        if (mimeType == null || mimeType.isEmpty()) {
            LogUtil.e( "Could not determine MIME type for URI: " + uri.toString());
            return; // 无法得到 mimeType ,放弃open操作. 打印Log。
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK); //  必须授权权限！

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent); //尝试打开
            } else {
                LogUtil.e( "No activity found to handle MIME type: " + mimeType);
            }

        } catch (Exception e) {
            LogUtil.e( "Error opening file: " + e.getMessage(), e);
        }
    }

    private static String getMimeTypeFromUri(Activity activity,Uri uri) {
        String extension;

        //Check uri format to avoid crash
        if (uri.getScheme().equals(android.content.ContentResolver.SCHEME_CONTENT)) {
            //If scheme is a content
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(activity.getContentResolver().getType(uri));
        } else {
            //If scheme is a file
            //This will replace white spaces with %20 and also other special characters. This will avoid returning null values on Content resolver
            //scheme can not be File , since here, should be Content.
            try {
                String filename =getFileByUri(uri,activity);

                extension = MimeTypeMap.getFileExtensionFromUrl(filename);

            }catch (Exception ex){
                return  null ;
            }
        }
        return extension;

    }
    private static  String getFileByUri(Uri uri,Activity activity){

        String img_path;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = activity.getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null){
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            img_path = cursor.getString(column_index);//This get error if not media.
            cursor.close();
            return img_path;
        }

        //for  images content type is: images/images
        else if (String.valueOf(uri).contains("images")){
            String mCurrentPhotoPath ="";
            String wholeID = DocumentsContract.getDocumentId(uri);

            // Split at colon, use second item in the array
            String id = wholeID.split(":")[1];
            String[] column = {MediaStore.Images.Media.DATA};
            // where id is equal to
            String sel = MediaStore.Images.Media._ID + "=?";

            Cursor cursorImage = activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    column, sel, new String[]{id}, null);
            int columnIndex = cursorImage.getColumnIndex(column[0]);

            if (cursorImage.moveToFirst()) {
                mCurrentPhotoPath = cursorImage.getString(columnIndex);
            }
            cursorImage.close();
            return  mCurrentPhotoPath ;
        }
        return "Unknow uri";

    }
}
