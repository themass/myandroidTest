package com.qq.ext.util.glide;

import android.content.Context;

import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory;
import com.qq.ext.util.FileUtils;

import java.io.File;

public final class MyGlideDiskCacheFactory extends DiskLruCacheFactory {
    public MyGlideDiskCacheFactory(Context context) {
        this(context, "glide_cache", 262144000);
    }

    public MyGlideDiskCacheFactory(Context context, int diskCacheSize) {
        this(context, "glide_cache", diskCacheSize);
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