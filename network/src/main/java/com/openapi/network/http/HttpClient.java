package com.openapi.network.http;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.openapi.common.util.log.LogUtil;
import com.openapi.network.R;
import com.openapi.network.dns.DnsRequestInterceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * 请求队列：取代 Volley 的 RequestQueue 与 HttpClient。
 *
 * 传输用 OkHttp；请求在固定大小的线程池里执行，结果在主线程投递。
 * 失败（超时、断网）重试一次，与原来的 DefaultRetryPolicy(…, 1, …) 一致；
 * 服务器返回非 2xx 不重试。
 */
public class HttpClient {

    /** 线程数，与原 Volley 队列一致。 */
    private static final int THREADS = 6;
    /** 首次 + 一次重试。 */
    private static final int MAX_ATTEMPTS = 2;

    public interface RequestFilter {
        boolean apply(ApiRequest<?> request);
    }

    private static Context context;
    private static OkHttpClient client;
    private static ExecutorService executor;
    private static OkHttpClient uploadClient;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Set<ApiRequest<?>> active = ConcurrentHashMap.newKeySet();

    private HttpClient() {
    }

    public static void init(boolean debug, Context context) {
        HttpClient.context = context;
        client = new OkHttpClient.Builder()
                .connectTimeout(40, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .addInterceptor(new DnsRequestInterceptor())
                .build();
        // 带文件的上传（图片识别等）服务端处理较慢：单独用更长的读写超时，避免 40 秒就被判失败
        uploadClient = client.newBuilder()
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build();
        executor = Executors.newFixedThreadPool(THREADS);
    }

    /** 处理网络请求异常：提示给用户。 */
    public static void showVolleyError(NetError error) {
        LogUtil.e(error.getMessage(), error);
        Toast.makeText(context, getVolleyError(error), Toast.LENGTH_SHORT).show();
    }

    public static String getVolleyError(NetError error) {
        if (error instanceof NetError.NetworkError) {
            return context.getResources().getString(R.string.error_network_no_connection);
        } else if (error instanceof NetError.TimeoutError) {
            return context.getResources().getString(R.string.error_network_timeout);
        } else if (error instanceof NetError.BusinessError) {
            return ((NetError.BusinessError) error).getMsg();
        }
        return context.getResources().getString(R.string.error_network_unknown);
    }

    /** 将请求加入队列。 */
    public static void addRequest(ApiRequest<?> request) {
        active.add(request);
        executor.execute(() -> run(request));
    }

    /** 取消带有该 tag 的请求。 */
    public static void cancelRequest(String tag) {
        LogUtil.i("cancelRequest tag=" + tag);
        cancelAll(request -> tag != null && tag.equals(request.getTag()));
    }

    /** 取消满足条件的请求。 */
    public static void cancelRequest(RequestFilter filter) {
        cancelAll(filter);
    }

    private static void cancelAll(RequestFilter filter) {
        for (ApiRequest<?> request : active) {
            if (filter.apply(request)) {
                request.cancel();
            }
        }
    }

    private static <T> void run(ApiRequest<T> request) {
        try {
            if (request.isCanceled()) {
                return;
            }
            Response<T> response = execute(request);
            mainHandler.post(() -> {
                if (!request.isCanceled()) {
                    request.deliver(response);
                }
            });
        } finally {
            active.remove(request);
        }
    }

    private static <T> Response<T> execute(ApiRequest<T> request) {
        NetError last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (request.isCanceled()) {
                return Response.error(new NetError("canceled"));
            }
            try {
                NetResponse response = perform(request);
                try {
                    return request.parseNetworkResponse(response);
                } catch (Exception e) {
                    return Response.error(new NetError.ParseError(e));
                }
            } catch (NetError.TimeoutError | NetError.NetworkError e) {
                last = e;
            } catch (NetError e) {
                return Response.error(e);
            }
        }
        return Response.error(last);
    }

    private static NetResponse perform(ApiRequest<?> request) throws NetError {
        Request.Builder builder;
        try {
            builder = new Request.Builder().url(request.getUrl());
        } catch (IllegalArgumentException e) {
            throw new NetError(e);
        }
        RequestBody body = request.buildBody();
        if (body != null) {
            builder.post(body);
        } else {
            builder.get();
        }
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    builder.addHeader(e.getKey(), e.getValue());
                }
            }
        }
        List<File> files = request.getFiles();
        OkHttpClient httpClient = (files != null && !files.isEmpty() && uploadClient != null) ? uploadClient : client;
        Call call = httpClient.newCall(builder.build());
        request.attachCall(call);
        try (okhttp3.Response okResponse = call.execute()) {
            byte[] data = readBody(okResponse);
            int code = okResponse.code();
            if (code < 200 || code > 299) {
                throw new NetError.ServerError(code, data);
            }
            Map<String, String> responseHeaders = new HashMap<>();
            for (String name : okResponse.headers().names()) {
                responseHeaders.put(name, okResponse.header(name));
            }
            return new NetResponse(code, responseHeaders, data);
        } catch (SocketTimeoutException e) {
            throw new NetError.TimeoutError(e);
        } catch (IOException e) {
            if (request.isCanceled()) {
                throw new NetError("canceled");
            }
            throw new NetError.NetworkError(e);
        }
    }

    /**
     * 读取响应体。请求头里自己加了 Accept-Encoding: gzip，OkHttp 不会替我们解压，
     * 所以这里按 Content-Encoding 手动解压。
     */
    private static byte[] readBody(okhttp3.Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            return new byte[0];
        }
        byte[] raw = body.bytes();
        String encoding = response.header("Content-Encoding");
        if (encoding == null || !encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
            return raw;
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
