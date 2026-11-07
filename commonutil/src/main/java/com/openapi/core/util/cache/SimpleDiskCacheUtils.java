package com.openapi.core.util.cache;

import com.openapi.core.util.LogUtil;

import android.content.Context;
import android.text.TextUtils;

import com.openapi.core.util.PackageUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wjying on 13-12-22.
 */
@SuppressWarnings("deprecation")
public class SimpleDiskCacheUtils {
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 50; // 10MB
    private static final String DISK_CACHE_DIR = "sex_cache";
    private static final Object sLockObject = new Object();
    private static SimpleDiskCache sSimpleDiskCache;
    private static Map<String, CacheTask> sCacheTasks = new HashMap<>();

    public static SimpleDiskCache getSimpleDiskCache(Context context) {
        synchronized (sLockObject) {
            if (sSimpleDiskCache == null) {
                File cacheDir = new File(context.getExternalCacheDir(), DISK_CACHE_DIR);
                int version = PackageUtils.getAppVersionCode(context);
                try {
                    sSimpleDiskCache = SimpleDiskCache.open(cacheDir, version, DISK_CACHE_SIZE);
                } catch (Exception e) {
                    LogUtil.e(e);
                }
            }
        }
        return sSimpleDiskCache;
    }

    public static void postCacheTask(String key, CacheTask task) {
        if (TextUtils.isEmpty(key) || task == null) {
            return;
        }
        CacheTask old = sCacheTasks.get(key);
        if (old != null) {
            old.cancel();
        }
        sCacheTasks.put(key, task);
        task.execute();
    }

    public static void removeCacheTask(String key) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        CacheTask old = sCacheTasks.remove(key);
        if (old != null) {
            old.cancel();
        }
    }

    public static boolean isCacheTaskRunning(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        CacheTask old = sCacheTasks.remove(key);
        if (old == null) {
            return false;
        }

        return !old.isCancelled();
    }

}
