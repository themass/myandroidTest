package com.openapi.common.net;

/**
 * 服务端返回的业务错误（errno 不成功时带 error 文案）。
 * Created by openapi on 2016/8/16.
 */
public class MyVolleyError extends NetError {
    private final String msg;

    public MyVolleyError(String msg) {
        super(msg);
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
