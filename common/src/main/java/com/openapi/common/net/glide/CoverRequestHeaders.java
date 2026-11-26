package com.openapi.common.net.glide;

import android.content.Context;

import com.openapi.common.net.http.NetConfig;
import com.openapi.common.net.request.UserAgent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 封面/图片请求要带的身份头（A40）。
 *
 * 服务端 CDN 按 UA 放行，API 的解密密钥又取 UA 末 16 位（{@code BaseRequest.authkey}）；
 * Glide 这条 OkHttp 链原先一个身份头都不带，封面因此时好时坏（403）。
 * UA 现取 {@link UserAgent}——与 API 同一份生成逻辑，不复制粘贴第二份；
 * UA 的签名段每次调用都不同（旧实现性质如此），CDN 只看前缀身份段，
 * 所以每次封面请求现生成一个 UA 没有问题。
 */
public final class CoverRequestHeaders {

    private CoverRequestHeaders() {
    }

    public static String userAgent(Context context) {
        return UserAgent.build(context);
    }

    /** 服务端派生密钥用的 UA 末 16 位；与 {@code BaseRequest} 的取法一致。 */
    public static String authkeyFor(String ua) {
        return ua.substring(ua.length() - 16);
    }

    public static Map<String, String> build(Context context) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", userAgent(context));
        String referer = NetConfig.defaultReferer();
        if (referer != null && !referer.isEmpty()) {
            h.put("Referer", referer);
        }
        return Collections.unmodifiableMap(h);
    }
}
