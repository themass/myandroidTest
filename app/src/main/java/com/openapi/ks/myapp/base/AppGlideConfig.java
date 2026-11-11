package com.openapi.ks.myapp.base;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.openapi.network.glide.MyGlideDiskCacheFactory;
import com.openapi.network.glide.OkHttpUrlLoader;
import com.openapi.network.unsafe.UnsafeOkHttpClient;
import com.openapi.common.util.log.LogUtil;

import java.io.InputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;

/**
 * 应用唯一的 {@link AppGlideModule}。Glide 规定只有应用模块能有它，库模块只提供组件。
 * 配置和原来 :commonutil 里的 MyGlideModule 完全一样：
 * 缓存大小、默认解码格式；网络加载在 OkHttp 官方集成之后追加一个自定义 DNS 的 OkHttp 加载器。
 */
@GlideModule
public final class AppGlideConfig extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new MyGlideDiskCacheFactory(context, "myglide", 100 * 1024 * 1024));
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context).build();
        int defaultMemoryCacheSize = calculator.getMemoryCacheSize();
        int defaultBitmapPoolSize = calculator.getBitmapPoolSize();
        builder.setMemoryCache(new LruResourceCache(defaultMemoryCacheSize));
        builder.setBitmapPool(new LruBitmapPool(defaultBitmapPoolSize));
        LogUtil.i("glide cache size PoolSize=" + defaultBitmapPoolSize + " ;CacheSize=" + defaultMemoryCacheSize);
        builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient(context);
        registry.append(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory((Call.Factory) okHttpClient));
    }
}
