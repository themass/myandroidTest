package com.openapi.common.net.glide;

import android.content.Context;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Glide 加载网络图片用的 OkHttp 客户端：**使用系统默认的证书和主机名校验**，只替换 DNS。
 *
 * 之前这里叫 UnsafeOkHttpClient：信任所有证书、主机名校验永远返回 true。
 * 这会让任何人在网络中间伪造证书都能替换图片，且 Google Play 会把自定义的「信任全部」
 * TrustManager / HostnameVerifier 判为 Security 违规并拒绝上架。
 * 现在服务端 api/file 域名都有有效证书，没有保留它的理由。
 *
 * A40「封面经常出不来」修的两件事：
 * - **UA/Referer**：CDN 按 UA 放行（服务端密钥也取 UA 末 16 位），这条链原先一个身份头
 *   都不带，随机 403。头由 {@link CoverRequestHeaders} 现生成——与 {@code BaseRequest}
 *   共用同一份 UA 逻辑（{@code UserAgent}），不复制第二份。
 * - **超时与重连**：没配超时时 OkHttp 用 10s/10s，一个挂死的连接占住 Glide 并发位，
 *   排在后面的封面全跟着不出来。显式设 connect 15s / read 30s（图片比 API 的 40s 敏感，
 *   用户都在盯着首屏），开 retryOnConnectionFailure 让瞬时断连先由 OkHttp 自己重试。
 */
public final class GlideHttpClient {

    private GlideHttpClient() {
    }

    public static OkHttpClient create(Context context) {
        final Context appContext = context.getApplicationContext();
        return new OkHttpClient.Builder()
                .dns(OkHttpDns.getInstance(context))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request.Builder builder = chain.request().newBuilder()
                                .header("User-Agent", CoverRequestHeaders.userAgent(appContext));
                        String referer = com.openapi.common.net.http.NetConfig.defaultReferer();
                        if (referer != null && !referer.isEmpty()) {
                            builder.header("Referer", referer);
                        }
                        return chain.proceed(builder.build());
                    }
                })
                .build();
    }
}
