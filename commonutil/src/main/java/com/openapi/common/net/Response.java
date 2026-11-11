package com.openapi.common.net;

/**
 * 请求结果：成功时带 [result]，失败时带 [error]。
 * 回调接口 [Listener] / [ErrorListener] 的形状与原来的 Volley 一致。
 */
public class Response<T> {

    public interface Listener<T> {
        void onResponse(T response);
    }

    public interface ErrorListener {
        void onErrorResponse(NetError error);
    }

    public final T result;
    public final NetError error;

    private Response(T result, NetError error) {
        this.result = result;
        this.error = error;
    }

    public static <T> Response<T> success(T result) {
        return new Response<>(result, null);
    }

    public static <T> Response<T> error(NetError error) {
        return new Response<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
