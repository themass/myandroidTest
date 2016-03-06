package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import java.util.Map;

public class MultipartRequest<T> extends BaseRequest<T> {
    private Map<String, String> param;

    public MultipartRequest(Context context, Map<String, String> param,
                            String url, Map<String, String> headers, Listener<T> listener,
                            ErrorListener errorListener) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.param = param;
    }

    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        return param;
    }

    @Override
    protected Map<String, String> getPostParams() throws AuthFailureError {
        return param;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return null;
    }

}
