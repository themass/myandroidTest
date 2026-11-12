package com.openapi.common.exce;

import com.android.volley.VolleyError;

/**
 * Created by dengt on 2016/8/16.
 */
public class MyVolleyError extends VolleyError {
    private String msg;
    /** 服务端返回的错误码。0 表示构造方没带（旧调用点），排障时用它区分失败类型。 */
    private int errno;

    public MyVolleyError(String msg) {
        this.msg = msg;
    }

    public MyVolleyError(int errno, String msg) {
        this.errno = errno;
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public int getErrno() {
        return errno;
    }

    /**
     * 服务端的 error 字段可能为空（某些分支只设置 errno，没有对应文案），
     * 那时 msg 是 null，日志里只剩一句 "null"。至少把 errno 带出来。
     */
    @Override
    public String toString() {
        return "MyVolleyError{errno=" + errno + ", msg=" + msg + '}';
    }
}
