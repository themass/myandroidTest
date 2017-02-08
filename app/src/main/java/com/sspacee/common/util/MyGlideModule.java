package com.sspacee.common.util;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.ExternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.GlideModule;
import com.sspacee.common.net.OkHttpStack;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class MyGlideModule implements GlideModule {
    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, 1024 * 1024 * 100));//内部磁盘缓存
        builder.setDiskCache(new ExternalCacheDiskCacheFactory(context, 100 * 1024 * 1024));//磁盘缓存到外部存储
        MemorySizeCalculator calculator = new MemorySizeCalculator(context);
        int defaultMemoryCacheSize = calculator.getMemoryCacheSize();
        int defaultBitmapPoolSize = calculator.getBitmapPoolSize();
        builder.setMemoryCache(new LruResourceCache(defaultMemoryCacheSize));
        builder.setBitmapPool(new LruBitmapPool(defaultBitmapPoolSize));
        LogUtil.i("glide cache size PoolSize="+defaultBitmapPoolSize+" ;CacheSize="+defaultMemoryCacheSize);
    }

    @Override
    public void registerComponents(Context context, Glide glide) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        // set your timeout here
        builder.connectTimeout(OkHttpStack.CONNECT_TIMEOUT, TimeUnit.SECONDS);
        builder.readTimeout(OkHttpStack.DEFUAT_TIMEOUT, TimeUnit.SECONDS);
        builder.writeTimeout(OkHttpStack.WRITE_TIMEOUT, TimeUnit.SECONDS);
        glide.register(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(builder.build()));
        LogUtil.i("MyGlideModule reg OK");
    }
}