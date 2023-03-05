package com.sspacee.common.util.netglide;

import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.util.ContentLengthInputStream;
import com.bumptech.glide.util.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OkHttpUrlLoader implements ModelLoader<GlideUrl, InputStream> {

private final Call.Factory client;

// Public API.
@SuppressWarnings("WeakerAccess")
public OkHttpUrlLoader(@NonNull Call.Factory client) {
    this.client = client;
}

@Override
public boolean handles(@NonNull GlideUrl url) {
    return true;
}

@Override
public LoadData<InputStream> buildLoadData(
        @NonNull GlideUrl model, int width, int height, @NonNull Options options) {
    return new LoadData<>(model, new OkHttpStreamFetcher(client, model));
}

public static class Factory implements ModelLoaderFactory<GlideUrl, InputStream> {
    private static volatile Call.Factory internalClient;
    private final Call.Factory client;

    private static Call.Factory getInternalClient() {
        if (internalClient == null) {
            synchronized (Factory.class) {
                if (internalClient == null) {
                    internalClient = new OkHttpClient();
                }
            }
        }
        return internalClient;
    }

    public Factory() {
        this(getInternalClient());
    }

    public Factory(@NonNull Call.Factory client) {
        this.client = client;
    }

    @NonNull
    @Override
    public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory multiFactory) {
        return new OkHttpUrlLoader(client);
    }

    @Override
    public void teardown() {
        // Do nothing, this instance doesn't own the client.
    }
}
}