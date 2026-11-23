package com.openapi.common.net.glide;

import android.content.Context;

import okhttp3.OkHttpClient;

/**
 * Glide 加载网络图片用的 OkHttp 客户端：**使用系统默认的证书和主机名校验**，只替换 DNS。
 *
 * 之前这里叫 UnsafeOkHttpClient：信任所有证书、主机名校验永远返回 true。
 * 这会让任何人在网络中间伪造证书都能替换图片，且 Google Play 会把自定义的「信任全部」
 * TrustManager / HostnameVerifier 判为 Security 违规并拒绝上架。
 * 现在服务端 api/file 域名都有有效证书，没有保留它的理由。
 */
public final class GlideHttpClient {

    private GlideHttpClient() {
    }

    public static OkHttpClient create(Context context) {
        return new OkHttpClient.Builder()
                .dns(OkHttpDns.getInstance(context))
                .build();
    }
}
