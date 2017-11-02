package com.sspacee.common.util;

import android.content.Context;

import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory;

import java.io.File;

public final class MyGlideDiskCacheFactory extends DiskLruCacheFactory {
    public MyGlideDiskCacheFactory(Context context) {
        this(context, "image_manager_disk_cache", 262144000);
    }

    public MyGlideDiskCacheFactory(Context context, int diskCacheSize) {
        this(context, "image_manager_disk_cache", diskCacheSize);
    }

    public MyGlideDiskCacheFactory(final Context context, final String diskCacheName, int diskCacheSize) {
        super(new CacheDirectoryGetter() {
            public File getCacheDirectory() {
                String cacheDirectory = FileUtils.getWriteFilePath(context);
                return new File(cacheDirectory, diskCacheName);
            }
        }, diskCacheSize);
    }
}