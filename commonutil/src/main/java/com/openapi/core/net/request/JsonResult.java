package com.openapi.core.net.request;

public class JsonResult<T> {
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
