package com.openapi.yewu.net.interceptor;

import com.openapi.common.util.LogUtil;
import com.openapi.yewu.net.HttpDNSUtil;

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
        Request.Builder builder = originRequest.newBuilder();
        builder.url(HttpDNSUtil.getIPByHost(url));
        builder.header("host", httpUrl.host());
        Request newRequest = builder.build();
        LogUtil.i("http newUrl:" + newRequest.url());
        return chain.proceed(newRequest);
    }

}