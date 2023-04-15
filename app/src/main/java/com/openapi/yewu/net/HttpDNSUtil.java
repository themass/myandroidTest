package com.openapi.yewu.net;

import android.net.Uri;

import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.StringUtils;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.StaticDataUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpDNSUtil {
    public static String DNS_POD_IP = "119.29.29.29";
    private static Map<String, Integer> countMap = new HashMap<>();
    /**
     * 根据url获得ip,此方法只是最简单的模拟,实际情况很复杂,需要做缓存处理
     *
     * @param host
     * @return
     */
    private static Pattern pattern = Pattern.compile("[0-9]*");

    /**
     * 转换url 主机头为ip地址
     *
     * @param url  原url
     * @param host 主机头
     * @param ip   服务器ip
     * @return
     */
    public static String getIpUrl(String url, String host, String ip) {
        if (url == null || host == null || ip == null) return url;
        String ipUrl = url.replaceFirst(host, ip);
        return ipUrl;
    }

    public static String getIPByHost(String url) {
        boolean needDnspod = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEED_DNSPOD_CONFIG, true) && url.contains("api.sspacee.com");
        if (!needDnspod) {
            return url;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String str = host.replaceAll("[.]", "");
        if (pattern.matcher(str).matches()) {
            return url;
        }
        String ipStr = StaticDataUtil.get(host, String.class);
        LogUtil.i("host="+host+"; ipStr="+ipStr);
        if (ipStr == null) {
            Integer errorCount = countMap.get(ipStr);
            errorCount = (errorCount == null) ? 0 : errorCount;
            if (errorCount >= 5) {
                return url;
            }
            HttpUrl httpUrl = new HttpUrl.Builder()
                    .scheme("http")
                    .host(DNS_POD_IP)
                    .addPathSegment("d")
                    .addQueryParameter("dn", host)
                    .build();
            //与我们正式请求独立，所以这里新建一个OkHttpClient
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build();
            String result = null;
            try {
                /**
                 * 子线程中同步去获取
                 */
                Response response = okHttpClient.newCall(request).execute();
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    LogUtil.i("dnsPod return " + body);
                    if (StringUtils.hasText(body)) {
                        String[] ips = body.split(";");
                        StaticDataUtil.add(host, ips[0]);
                        result = ips[0];
                        LogUtil.i("HttpDNS the host has replaced with ip " + result);
                        return url.replaceFirst(host, result);
                    } else {
                        countMap.put(ipStr, errorCount + 1);
                    }
                }
            } catch (Exception e) {
                countMap.put(ipStr, errorCount + 1);
                LogUtil.e("HttpDNS origin host: " + host + ";dDNSpod ret:" + result, e);
            }
        } else {
            LogUtil.i("HttpDNS the host has replaced with ip " + ipStr);
            return url.replaceFirst(host, ipStr);
        }
        return url;
    }
}