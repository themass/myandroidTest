//package com.openapi.core.net.netglide;
//
//import android.content.Context;
//
//import androidx.annotation.Nullable;
//
//import com.bumptech.glide.Glide;
//import com.bumptech.glide.Registry;
//import com.bumptech.glide.annotation.GlideModule;
//import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
//import com.bumptech.glide.load.DataSource;
//import com.bumptech.glide.load.engine.GlideException;
//import com.bumptech.glide.load.model.GlideUrl;
//import com.bumptech.glide.module.LibraryGlideModule;
//import com.bumptech.glide.request.RequestListener;
//import com.bumptech.glide.request.target.Target;
//import com.openapi.core.util.LogUtil;
//import com.openapi.core.net.OkHttpStack;
//
//import java.io.InputStream;
//import java.util.concurrent.TimeUnit;
//
//import okhttp3.Call;
//import okhttp3.OkHttpClient;
//
//import static android.R.attr.resource;
//
//@GlideModule
//public class MyGlideLibModule extends LibraryGlideModule {
//
//    @Override
//    public void registerComponents(Context context, Glide glide, Registry registry) {
//        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
//        // set your timeout here
//        builder.connectTimeout(OkHttpStack.CONNECT_TIMEOUT, TimeUnit.SECONDS);
//        builder.readTimeout(OkHttpStack.DEFUAT_TIMEOUT, TimeUnit.SECONDS);
//        builder.writeTimeout(OkHttpStack.WRITE_TIMEOUT, TimeUnit.SECONDS);
//        OkHttpClient okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient(context);
////        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory((Call.Factory) builder.build()));
//        registry.append(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory((Call.Factory) okHttpClient));
//
//        LogUtil.i("MyGlideModule reg OK");
//    }
//
//
//}