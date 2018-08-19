package com.qq.ext.except;

import com.android.volley.VolleyError;

/**
 * Created by dengt
 *
 * */
public class MVolleyError extends com.android.volley.VolleyError {
    public String msg;
    public MVolleyError(String msg){
        this.msg = msg;
    }
}
