package com.openapi.commons.common.exce;

/**
 * Created by openapi on 2016/3/8.
 */
public class JsonResultException extends RequestException {
    public JsonResultException(String content) {
        super(content);
    }
}
