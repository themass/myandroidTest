package com.sspacee.yewu.net;

import com.sspacee.common.exce.HttpException;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.net.interceptor.GzipRequestInterceptor;
import com.sspacee.yewu.net.interceptor.LoggingInterceptor;
import com.timeline.vpn.constant.Constants;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpUtil {
    private static final String CHARSET_NAME = "UTF-8";
    private static OkHttpClient mOkHttpClient;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(new GzipRequestInterceptor()).connectTimeout(Constants.connTimeOut, TimeUnit.SECONDS).readTimeout(Constants.connTimeOut, TimeUnit.SECONDS);
        builder.addInterceptor(new LoggingInterceptor());
        mOkHttpClient = builder.build();
    }

    /**
     * 该不会开启异步线程。
     *
     * @param request
     * @return
     * @throws IOException
     */
    public static Response execute(Request request) throws IOException {
        return mOkHttpClient.newCall(request).execute();
    }

    /**
     * 开启异步线程访问网络
     *
     * @param request
     * @param responseCallback
     */
    public static void enqueue(Request request, Callback responseCallback) {
        mOkHttpClient.newCall(request).enqueue(responseCallback);
    }

    /**
     * 开启异步线程访问网络, 且不在意返回结果（实现空callback）
     *
     * @param request
     */
    public static void enqueue(Request request) {
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
    }

    public static String getStringFromServer(String url) {
        try {
            Request request = new Request.Builder().url(url).headers(HttpUtils.getOkHeader()).build();
            Response response = execute(request);
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                LogUtil.e("Unexpected code " + response);
                throw new HttpException(Constants.NET_ERROR);
            }
        } catch (Exception e) {
            throw new HttpException(e);
        }
    }

    /**
     * 为HttpGet 的 url 方便的添加1个name value 参数。
     *
     * @param url
     * @param name
     * @param value
     * @return
     */
    public static String attachHttpGetParam(String url, String name, String value) {
        return url + "?" + name + "=" + value;
    }
}