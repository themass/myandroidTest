package com.openapi.common.net.http;

import java.util.Map;

/** 一次成功的 HTTP 往返：状态码、响应头（同名取第一个值）、已解压的响应体。 */
public class NetResponse {
    public final int statusCode;
    public final Map<String, String> headers;
    public final byte[] data;

    public NetResponse(int statusCode, Map<String, String> headers, byte[] data) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.data = data;
    }
}
