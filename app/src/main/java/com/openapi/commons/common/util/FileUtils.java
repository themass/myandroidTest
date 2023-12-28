package com.openapi.commons.common.util;

import android.content.Context;
import android.os.Environment;

import com.openapi.commons.common.CommonConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtils {
    private final static int BUFFER_SIZE = 1024;

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

    public static String getCharonFilePath() {
        return CommonConstants.tmpFilePath + File.separator + CommonConstants.LOG_FILE;
//        return context.getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
    }

    public static String getCharonUploadFilePath() {
        return CommonConstants.tmpFilePath + File.separator + CommonConstants.LOG_FILE_FOR_UPLOAD;
    }

    public static String getBugFilePath() {
        return CommonConstants.tmpFilePath + File.separator + CommonConstants.BUG_FILE;
    }

    public static String getBugUploadFilePath() {
        return CommonConstants.tmpFilePath + File.separator + CommonConstants.BUG_FILE_FOR_UPLOAD;
    }

    public static boolean mvFile(String srcFile, String decFile) {
        File file = new File(srcFile);
        if (file.exists()) {
            return file.renameTo(new File(decFile));
        } else {
            return false;
        }
    }

    public static String getWriteFilePath(Context context) {
        return context.getFilesDir().getAbsolutePath()+File.separator +"myfreesex";
//        String sdStatus = Environment.getExternalStorageState();
//        if (!sdStatus.equals(Environment.MEDIA_MOUNTED)) {
//            return context.getFilesDir().getAbsolutePath() + File.separator + "myfreesex";
//        } else {
//            return File.separator + "sdcard" + File.separator + "myfreesex";
//        }
    }

    public static boolean ensureFile(Context context, String filePath) {
        File path = new File(filePath);
        if (!path.exists()) {
            LogUtil.d("Create the path:" + filePath);
            path.mkdirs();
        }
        return true;
    }

    public static void writeLogFile(String content, String fileName) {
        File file = new File(CommonConstants.tmpFilePath, fileName);
        try {
            if (!file.exists()) {
                LogUtil.d("Create the file:" + fileName);
                file.createNewFile();
            }
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.seek(file.length());
            raf.write("/r/n".getBytes());
            raf.write(content.getBytes());
            raf.close();
        } catch (Exception e) {
            LogUtil.d("Error on writeFilToSD.");
        }
    }

    public static void delLogFile(String fileName) {
        File file = new File(CommonConstants.tmpFilePath, fileName);
        if (file.exists()) {
            file.delete();
        }

    }

}
