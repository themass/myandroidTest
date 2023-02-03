package com.sspacee.yewu.net;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.HttpStack;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.net.interceptor.DnsRequestInterceptor;
import com.sspacee.yewu.net.request.MultipartRequest;
import com.ks.myapp.base.MyApplication;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

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
 * OkHttp backed {@link com.android.volley.toolbox.HttpStack HttpStack} that does not
 * use okhttp-urlconnection
 */
public class OkHttpStack implements HttpStack {
    public static final int DEFUAT_TIMEOUT = 20;
    public static final int CONNECT_TIMEOUT = 10;
    public static final int WRITE_TIMEOUT = 30;
    private static OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    private static OkHttpClient mClient;

    static {
        clientBuilder.connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS);
        clientBuilder.readTimeout(DEFUAT_TIMEOUT, TimeUnit.SECONDS);
        clientBuilder.writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS);
        if (MyApplication.isDebug) {
            clientBuilder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        }
//        clientBuilder.addInterceptor(new GzipRequestInterceptor());
        clientBuilder.addInterceptor(new DnsRequestInterceptor());
        mClient = clientBuilder.build();
    }

    private static HttpEntity entityFromOkHttpResponse(Response r) throws IOException {
        BasicHttpEntity entity = new BasicHttpEntity();
        ResponseBody body = r.body();
        entity.setContentLength(body.contentLength());
        entity.setContentEncoding(r.header("Content-Encoding"));
        boolean isGzipResponse = HttpUtils.isGzip(r.header("Content-Encoding"));
        if (body.contentType() != null) {
            entity.setContentType(body.contentType().type());
        }
        InputStream inputStream = body.byteStream();
        if (isGzipResponse) {
            inputStream = new GZIPInputStream(inputStream);
        }
        entity.setContent(inputStream);
        return entity;
    }

    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, com.android.volley.Request<?> request)
            throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // Ensure backwards compatibility.  Volley assumes a request with a null body is a GET.
                byte[] postBody = request.getBody();
                if (postBody != null) {
                    builder.post(RequestBody.create(MediaType.parse(request.getPostBodyContentType()), postBody));
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

    private static void setHeaderForRequest(okhttp3.Request.Builder builder, com.android.volley.Request<?> request, Map<String, String> additionalHeaders) throws AuthFailureError {
        Map<String, String> headers = request.getHeaders();
        for (final String name : headers.keySet()) {
            builder.addHeader(name, headers.get(name));
        }
        for (final String name : additionalHeaders.keySet()) {
            builder.addHeader(name, additionalHeaders.get(name));
        }
    }

    private static ProtocolVersion parseProtocol(final Protocol p) {
        switch (p) {
            case HTTP_1_0:
                return new ProtocolVersion("HTTP", 1, 0);
            case HTTP_1_1:
                return new ProtocolVersion("HTTP", 1, 1);
            case SPDY_3:
                return new ProtocolVersion("SPDY", 3, 1);
            case HTTP_2:
                return new ProtocolVersion("HTTP", 2, 0);
        }

        throw new IllegalAccessError("Unkwown protocol");
    }

    private static RequestBody createRequestBody(Request r) throws AuthFailureError {
        if (r instanceof MultipartRequest && ((MultipartRequest) r).getFile() != null) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            List<File> fileList = ((MultipartRequest) r).getFile();
            for (File file : fileList) {
                LogUtil.i("add file:" + file.getName());
                RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
                builder.addFormDataPart(((MultipartRequest) r).getName(), file.getName(), fileBody);
            }
            return builder.build();
        }
        final byte[] body = r.getBody();
        if (body == null) {
            return RequestBody.create(MediaType.parse(r.getBodyContentType()), new byte[0]);
        }
        return RequestBody.create(MediaType.parse(r.getBodyContentType()), body);
    }

    @Override
    public HttpResponse performRequest(com.android.volley.Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        setConnectionParametersForRequest(okHttpRequestBuilder, request);
        setHeaderForRequest(okHttpRequestBuilder, request, additionalHeaders);
        okHttpRequestBuilder.url(request.getUrl());
        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = mClient.newCall(okHttpRequest);
        Response okHttpResponse = okHttpCall.execute();
        StatusLine responseStatus = new BasicStatusLine(parseProtocol(okHttpResponse.protocol()), okHttpResponse.code(), okHttpResponse.message());
        BasicHttpResponse response = new BasicHttpResponse(responseStatus);
        response.setEntity(entityFromOkHttpResponse(okHttpResponse));
        Headers responseHeaders = okHttpResponse.headers();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i), value = responseHeaders.value(i);
            if (name != null) {
                response.addHeader(new BasicHeader(name, value));
            }
        }
        return response;
    }
}
