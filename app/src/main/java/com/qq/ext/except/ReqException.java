package com.qq.ext.except;

/**
 * Created by dengt on 2016/3/8.
 */
public class ReqException extends RuntimeException {
    public String msg;
    public Exception e;
    public ReqException(String msg,Exception e){
        this.msg = msg;
        this.e = e;
    }
}
