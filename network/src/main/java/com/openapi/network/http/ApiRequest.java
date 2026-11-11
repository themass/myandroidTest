package com.openapi.network.http;

import com.openapi.common.util.log.LogUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * 一个网络请求。取代 Volley 的 Request：子类声明方法、头、参数，以及如何解析响应体。
 * 由 [HttpClient] 在工作线程执行，结果在主线程回调。
 */
public abstract class ApiRequest<T> {

    public static final class Method {
        public static final int GET = 0;
        public static final int POST = 1;

        private Method() {
        }
    }

    private final int method;
    private final String url;
    private final Response.Listener<T> listener;
    private final Response.ErrorListener errorListener;
    private volatile Object tag;
    private volatile boolean canceled;
    private volatile Call call;

    protected ApiRequest(int method, String url, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        this.method = method;
        this.url = url;
        this.listener = listener;
        this.errorListener = errorListener;
    }

    public int getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    /** 额外的请求头；子类覆盖。 */
    public Map<String, String> getHeaders() {
        return new HashMap<>();
    }

    /** POST 的表单参数；无参数返回 null。 */
    protected Map<String, String> getParams() {
        return null;
    }

    /** 要以 multipart 上传的文件；没有文件返回 null。 */
    protected List<File> getFiles() {
        return null;
    }

    /** multipart 中文件字段的名字。 */
    protected String getFileField() {
        return null;
    }

    /** 把响应解析成结果；失败时返回携带 [NetError] 的 Response。 */
    protected abstract Response<T> parseNetworkResponse(NetResponse response);

    public boolean isCanceled() {
        return canceled;
    }

    /** 取消请求：未执行的不再执行，正在执行的中断连接，之后不再投递回调。 */
    public void cancel() {
        canceled = true;
        Call c = call;
        if (c != null) {
            c.cancel();
        }
    }

    void attachCall(Call c) {
        call = c;
        if (canceled) {
            c.cancel();
        }
    }

    /** POST 的请求体；GET 返回 null。有文件时为 multipart，否则为表单。 */
    RequestBody buildBody() {
        if (method == Method.GET) {
            return null;
        }
        Map<String, String> params = getParams();
        List<File> files = getFiles();
        if (files != null && !files.isEmpty()) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (File file : files) {
                LogUtil.i("add file:" + file.getName());
                RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
                builder.addFormDataPart(getFileField(), file.getName(), fileBody);
            }
            if (params != null) {
                for (Map.Entry<String, String> e : params.entrySet()) {
                    builder.addFormDataPart(e.getKey(), e.getValue());
                }
            }
            return builder.build();
        }
        FormBody.Builder form = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                form.add(e.getKey(), e.getValue());
            }
        }
        return form.build();
    }

    /** 在主线程调用。 */
    void deliver(Response<T> response) {
        if (response.isSuccess()) {
            deliverResponse(response.result);
        } else if (errorListener != null) {
            errorListener.onErrorResponse(response.error);
        }
    }

    protected void deliverResponse(T result) {
        try {
            if (listener != null) {
                listener.onResponse(result);
            }
        } catch (Exception e) {
            LogUtil.e("", e);
        }
    }
}
