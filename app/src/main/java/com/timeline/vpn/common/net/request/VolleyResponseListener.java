package com.timeline.vpn.common.net.request;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.timeline.vpn.common.net.VolleyUtils;

public abstract class VolleyResponseListener<T> implements Listener<T>, ErrorListener {

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        VolleyUtils.showVolleyError(volleyError);
    }

}
