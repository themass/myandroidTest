package com.timeline.vpn.bean.vo;

public class JsonResult<T> {
    public int errno;
    public String data;
    public T objData;
    public String error;
    public long cost;

    public T getData() {
        return objData;
    }

}
