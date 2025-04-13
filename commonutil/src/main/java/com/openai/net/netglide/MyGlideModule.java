package com.openai.net.netglide;

import static android.R.attr.resource;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.openai.commonutil.LogUtil;

import java.io.InputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;

@GlideModule
public class MyGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
//        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, 1024 * 1024 * 100));//内部磁盘缓存
        builder.setDiskCache(new MyGlideDiskCacheFactory(context, "myglide", 100 * 1024 * 1024));//磁盘缓存到外部存储
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context).build();
        int defaultMemoryCacheSize = calculator.getMemoryCacheSize();
        int defaultBitmapPoolSize = calculator.getBitmapPoolSize();
        builder.setMemoryCache(new LruResourceCache(defaultMemoryCacheSize));
        builder.setBitmapPool(new LruBitmapPool(defaultBitmapPoolSize));
        LogUtil.i("glide cache size PoolSize=" + defaultBitmapPoolSize + " ;CacheSize=" + defaultMemoryCacheSize);
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient(context);
        registry.append(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory((Call.Factory) okHttpClient));
    }
public static class LoggingListener<T> implements RequestListener<T> {
    @Override
    public boolean onLoadFailed(@Nullable GlideException e, Object o, Target<T> target, boolean b) {
        LogUtil.e(String.format("GLIDE-onException(%s, %s, %s, %s)", e, o, target, b), e);
        return false;
    }

    @Override
    public boolean onResourceReady(T t, Object o, Target<T> target, DataSource dataSource, boolean b) {
        LogUtil.i(String.format("GLIDE-onResourceReady(%s, %s, %s, %s, %s)", resource, o, target, dataSource.toString(), b));
        return false;
    }
}
}