package com.sspacee.common.net.interceptor;

import com.sspacee.common.net.HttpDNSUtil;
import com.sspacee.common.util.LogUtil;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class DnsRequestInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originRequest = chain.request();
        HttpUrl httpUrl = originRequest.url();
        String url = httpUrl.toString();
        String host = httpUrl.host();
        Request.Builder builder = originRequest.newBuilder();
        builder.url(HttpDNSUtil.getIPByHost(url, host));
        builder.header("host", host);
        Request newRequest = builder.build();
        LogUtil.i("http newUrl:" + newRequest.url());
        return chain.proceed(newRequest);
    }

}