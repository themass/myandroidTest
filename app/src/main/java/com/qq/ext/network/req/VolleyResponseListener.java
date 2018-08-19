package com.qq.ext.network.req;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.qq.ext.network.VolleyUtils;

public abstract class VolleyResponseListener<T> implements Listener<T>, ErrorListener {

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        VolleyUtils.showVolleyError(volleyError);
    }

}
