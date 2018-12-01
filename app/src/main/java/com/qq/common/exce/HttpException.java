package com.qq.common.exce;

/**
 * Created by dengt on 2016/3/8.
 */
public class HttpException extends RequestException {
    public HttpException(String content) {
        super(content);
    }

    public HttpException(Exception e) {
        super(e);
    }
}
