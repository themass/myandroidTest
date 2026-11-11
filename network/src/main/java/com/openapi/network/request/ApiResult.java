package com.openapi.network.request;

/**
 * 网络层的响应信封（服务端 JSON 的 errno / data / error）。成功时用 [getData]，业务错误由 BaseRequest 转成 NetError。
 */
public class ApiResult<T> {
    public int errno;
    public String data;
    public T objData;
    public String error;
    public String ip;
    public long cost;

    public T getData() {
        return objData;
    }

}
