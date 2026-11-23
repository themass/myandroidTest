package com.openapi.common.net.http;

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
        /** 没有业务码时的占位值（旧调用点只传文案）。 */
        public static final int NO_CODE = -1;

        private final String msg;
        private final int errno;

        public BusinessError(String msg) {
            this(NO_CODE, msg);
        }

        public BusinessError(int errno, String msg) {
            super(msg);
            this.msg = msg;
            this.errno = errno;
        }

        public String getMsg() {
            return msg;
        }

        /** 服务端 errno；按码分支的业务（如拍照解题的 42901 额度用完）用它，而不是匹配文案。 */
        public int getErrno() {
            return errno;
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
