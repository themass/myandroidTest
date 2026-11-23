package com.openapi.common.util.android;

import com.openapi.common.util.log.LogUtil;

import java.net.URL;

/**
 * 网址里取域名。
 *
 * 这里曾经还有「查本机公网 IP」：明文请求 ip.chinaz.com / pv.sohu.com，会把用户 IP 发给第三方。
 * 全仓没有调用方，Google Play 数据安全审查也会盯这种第三方明文请求，已删除。
 */
public class IpUtil {

    private IpUtil() {
    }

    public static String getDomain(String urlPath) {
        try {
            URL url = new URL(urlPath);
            return url.getHost();
        } catch (Exception e) {
            LogUtil.e(e);
            return "";
        }
    }
}
