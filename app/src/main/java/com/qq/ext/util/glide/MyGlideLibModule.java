package com.qq.ext.util.glide;

import android.content.Context;
import android.support.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.LibraryGlideModule;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.qq.ext.network.OkHttpStack;
import com.qq.ext.util.LogUtil;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

import static android.R.attr.resource;

@GlideModule
public class MyGlideLibModule extends LibraryGlideModule {

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        // set your timeout here
        builder.connectTimeout(OkHttpStack.CONNECT_TIMEOUT, TimeUnit.SECONDS);
        builder.readTimeout(OkHttpStack.DEFUAT_TIMEOUT, TimeUnit.SECONDS);
        builder.writeTimeout(OkHttpStack.WRITE_TIMEOUT, TimeUnit.SECONDS);
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(builder.build()));
        LogUtil.i("MyGlideModule reg OK");
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