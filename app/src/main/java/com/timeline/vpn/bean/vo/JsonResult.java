package com.timeline.vpn.bean.vo;

public class JsonResult<T>{
    public int errno;
    public T data;
    public String error;
    public long cost;
    public T getData(){
        return data;
    }

}
