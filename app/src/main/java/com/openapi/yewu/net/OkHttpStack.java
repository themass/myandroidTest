package com.openapi.yewu.net;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;
import com.openapi.common.util.LogUtil;
import com.openapi.yewu.net.interceptor.DnsRequestInterceptor;
import com.openapi.yewu.net.request.MultipartRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * OkHttp backed {@link BaseHttpStack BaseHttpStack} that does not
 * use okhttp-urlconnection
 */
public class OkHttpStack extends BaseHttpStack {
    public static final int DEFUAT_TIMEOUT = 40;
    public static final int CONNECT_TIMEOUT = 40;
    public static final int WRITE_TIMEOUT = 40;
    private static OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    private static OkHttpClient mClient;

    static {
        clientBuilder.connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS);
        clientBuilder.readTimeout(DEFUAT_TIMEOUT, TimeUnit.SECONDS);
        clientBuilder.writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS);
//        clientBuilder.addInterceptor(new GzipRequestInterceptor());
        clientBuilder.addInterceptor(new DnsRequestInterceptor());
        mClient = clientBuilder.build();
    }

    private static HttpResponse createVolleyResponse(Response okHttpResponse) throws IOException {
        ResponseBody body = okHttpResponse.body();
        int statusCode = okHttpResponse.code();
        String reasonPhrase = okHttpResponse.message();

        // 处理响应头 - 转换为List<Header>
        Headers responseHeaders = okHttpResponse.headers();
        List<com.android.volley.Header> headers = new java.util.ArrayList<>();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i), value = responseHeaders.value(i);
            if (name != null) {
                headers.add(new com.android.volley.Header(name, value));
            }
        }

        // 处理响应体
        InputStream inputStream = null;
        int contentLength = 0;
        if (body != null) {
            contentLength = (int) body.contentLength();
            inputStream = body.byteStream();

            // 处理GZIP压缩
            boolean isGzipResponse = HttpUtils.isGzip(okHttpResponse.header("Content-Encoding"));
            if (isGzipResponse) {
                inputStream = new GZIPInputStream(inputStream);
            }
        }

        return new HttpResponse(statusCode, headers, contentLength, inputStream);
    }

    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, Request<?> request)
            throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // Ensure backwards compatibility.  Volley assumes a request with a null body is a GET.
                byte[] postBody = request.getBody();
                if (postBody != null) {
                    MediaType mediaType = MediaType.parse(request.getBodyContentType());
                    builder.post(RequestBody.create(mediaType, postBody));
                }
                break;
            case Request.Method.GET:
                builder.get();
                break;
            case Request.Method.DELETE:
                builder.delete();
                break;
            case Request.Method.POST:
                builder.post(createRequestBody(request));
                break;
            case Request.Method.PUT:
                builder.put(createRequestBody(request));
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static void setHeaderForRequest(okhttp3.Request.Builder builder, Request<?> request, Map<String, String> additionalHeaders) throws AuthFailureError {
        Map<String, String> headers = request.getHeaders();
        for (final String name : headers.keySet()) {
            builder.addHeader(name, headers.get(name));
        }
        for (final String name : additionalHeaders.keySet()) {
            builder.addHeader(name, additionalHeaders.get(name));
        }
    }


    private static RequestBody createRequestBody(Request r) throws AuthFailureError {
        if (r instanceof MultipartRequest && ((MultipartRequest) r).getFile() != null) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            List<File> fileList = ((MultipartRequest) r).getFile();
            for (File file : fileList) {
                LogUtil.i("add file:" + file.getName());
                MediaType fileMediaType = MediaType.parse("application/octet-stream");
                RequestBody fileBody = RequestBody.create(file, fileMediaType);
                builder.addFormDataPart(((MultipartRequest) r).getName(), file.getName(), fileBody);
            }
            Map<String, String> param = ((MultipartRequest) r).getParams();
            LogUtil.i("添加 消息参数"+(param==null?" null":param.size()));
            if ( param!= null) {
                param.entrySet().forEach( o -> {
                    builder.addFormDataPart(o.getKey(), o.getValue());
                });
            }
            return builder.build();
        }
        final byte[] body = r.getBody();
        MediaType bodyMediaType = MediaType.parse(r.getBodyContentType());
        if (body == null) {
            return RequestBody.create(new byte[0], bodyMediaType);
        }
        return RequestBody.create(body, bodyMediaType);
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        setConnectionParametersForRequest(okHttpRequestBuilder, request);
        setHeaderForRequest(okHttpRequestBuilder, request, additionalHeaders);
        okHttpRequestBuilder.url(request.getUrl());
        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = mClient.newCall(okHttpRequest);
        Response okHttpResponse = okHttpCall.execute();

        // 创建Volley的HttpResponse
        return createVolleyResponse(okHttpResponse);
    }
}
