package com.timeline.vpn.common.exce;

/**
 * Created by gqli on 2016/3/8.
 */
public class RequestException extends RuntimeException{
    private String msg;
    private Exception e;
    public RequestException(String content) {
        this.msg = content;
    }
    public RequestException(Exception e) {
        this.e = e;
    }
    public String getErrorMsg(){
        return msg;
    }
    public Exception getException(){
        return e;
    }
}
