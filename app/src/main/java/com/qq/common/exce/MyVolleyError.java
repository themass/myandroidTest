package com.qq.common.exce;

import com.android.volley.VolleyError;

/**
 * Created by dengt on 2016/8/16.
 */
public class MyVolleyError extends VolleyError {
    private String msg;

    public MyVolleyError(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

}
