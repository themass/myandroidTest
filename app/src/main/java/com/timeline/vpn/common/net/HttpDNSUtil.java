package com.timeline.vpn.common.net;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.data.StaticDataUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpDNSUtil {
    /**
     * 转换url 主机头为ip地址
     *
     * @param url  原url
     * @param host 主机头
     * @param ip   服务器ip
     * @return
     */
    public static String getIpUrl(String url, String host, String ip) {
        if (url == null) {
            LogUtil.e("URL NULL");
        }
        if (host == null) {
            LogUtil.e( "host NULL");
        }
        if (ip == null) {
            LogUtil.e("ip NULL");
        }
        if (url == null || host == null || ip == null) return url;
        String ipUrl = url.replaceFirst(host, ip);
        return ipUrl;
    }
    /**
     * 根据url获得ip,此方法只是最简单的模拟,实际情况很复杂,需要做缓存处理
     *
     * @param host
     * @return
     */
    public static String getIPByHost(String host) {
        String ipStr = StaticDataUtil.get(host,String.class);
        if(ipStr==null) {
            HttpUrl httpUrl = new HttpUrl.Builder()
                    .scheme("http")
                    .host("119.29.29.29")
                    .addPathSegment("d")
                    .addQueryParameter("host", host)
                    .build();
            //与我们正式请求独立，所以这里新建一个OkHttpClient
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build();
            try {
                String result = null;
                /**
                 * 子线程中同步去获取
                 */
                Response response = okHttpClient.newCall(request).execute();
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JSONObject jsonObject = new JSONObject(body);
                    JSONArray ips = jsonObject.optJSONArray("ips");
                    if (ips != null) {
                        result = ips.optString(0);
                        StaticDataUtil.add(host,result);
                    }
                }
                return result;
            } catch (Exception e) {
              LogUtil.e(e);
            }
        }else{
            return ipStr;
        }
        return null;
    }
}