package com.sspacee.common.util;

import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class MemoryStatus {

    private static final int ERROR = -1;

    /**
     * sdcard 是否可用
     *
     * @return flag
     */
    public static boolean externalMemoryAvailable() {
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)
                && Environment.getExternalStorageDirectory()
                .canWrite();
    }

    /**
     * 返回内部存储可用大小，单位为字节
     *
     * @return ｌｏｎｇ
     */
    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    /**
     * 返回内部存储总大小 ，单位为字节
     *
     * @return ｌｏｎｇ
     */
    public static long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    /**
     * 返回 sdcard 可用大小 ，单位为字节
     *
     * @return ｌｏｎｇ
     */
    public static long getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } else {
            return ERROR;
        }
    }

    /**
     * 返回 sdcard 总容量 ，单位为字节
     *
     * @return ｌｏｎｇ
     */
    public static long getTotalExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return totalBlocks * blockSize;
        } else {
            return ERROR;
        }
    }

}
