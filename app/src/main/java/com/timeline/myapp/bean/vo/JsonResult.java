package com.timeline.myapp.bean.vo;

public class JsonResult<T> {
    public int errno;
    public String data;
    public T objData;
    public String error;
    public String ip;
    public long cost;
    public String userIp;
    public T getData() {
        return objData;
    }

}
