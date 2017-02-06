package com.sspacee.common.exce;

/**
 * Created by themass on 2016/3/8.
 */
public class HttpException extends RequestException {
    public HttpException(String content) {
        super(content);
    }

    public HttpException(Exception e) {
        super(e);
    }
}
