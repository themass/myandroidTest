package com.timeline.vpn.common.net.interceptor;

import com.timeline.vpn.common.net.HttpDNSUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.data.StaticDataUtil;

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
        LogUtil.i("HttpDNS origin url:" + url);
        LogUtil.i("HttpDNS origin host:" + host);
        String hostIP = StaticDataUtil.get(host,String.class);
        Request.Builder builder = originRequest.newBuilder();
        if (hostIP != null) {
            builder.url(HttpDNSUtil.getIpUrl(url, host, hostIP));
            builder.header("host", host);
            LogUtil.i("HttpDNS the host has replaced with ip " + hostIP);
        } else {
            LogUtil.i("HttpDNS can't get the ip , can't replace the host");
        }

        Request newRequest = builder.build();
        LogUtil.i("HttpDNS newUrl:" + newRequest.url());
        return chain.proceed(newRequest);
    }

}