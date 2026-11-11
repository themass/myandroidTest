package com.openapi.network.http;

/**
 * 网络请求失败的原因。取代 Volley 的 VolleyError，类型名和层级保持一致，便于迁移。
 */
public class NetError extends Exception {

    public NetError(String message) {
        super(message);
    }

    public NetError(Throwable cause) {
        super(cause);
    }

    /** 没有网络、连接被拒、连接中断等。 */
    public static class NetworkError extends NetError {
        public NetworkError(Throwable cause) {
            super(cause);
        }
    }

    /** 连接或读取超时。 */
    public static class TimeoutError extends NetError {
        public TimeoutError(Throwable cause) {
            super(cause);
        }
    }

    /** 响应体无法解析（JSON 格式错误、字段缺失等）。 */
    public static class ParseError extends NetError {
        public ParseError(Throwable cause) {
            super(cause);
        }
    }

    /** 服务端返回的业务错误（errno 不成功时带 error 文案），文案直接展示给用户。 */
    public static class BusinessError extends NetError {
        private final String msg;

        public BusinessError(String msg) {
            super(msg);
            this.msg = msg;
        }

        public String getMsg() {
            return msg;
        }
    }

    /** 服务器返回了非 2xx 状态码。 */
    public static class ServerError extends NetError {
        public final int statusCode;
        public final byte[] data;

        public ServerError(int statusCode, byte[] data) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.data = data;
        }
    }
}
