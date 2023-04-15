package com.openapi.commons.common.exce;

/**
 * Created by openapi on 2016/3/8.
 */
public class RequestException extends RuntimeException {
    private String msg;
    private Exception e;

    public RequestException(String content) {
        this.msg = content;
    }

    public RequestException(Exception e) {
        this.e = e;
    }

    public String getErrorMsg() {
        return msg;
    }

    public Exception getException() {
        return e;
    }
}
