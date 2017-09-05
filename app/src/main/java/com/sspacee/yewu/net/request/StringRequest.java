package com.sspacee.yewu.net.request;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;

public class StringRequest extends BaseRequest<String> {
    public StringRequest(Context context, int method, String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(context, method, url, null, listener, errorListener);
    }

    public StringRequest(Context context, String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        this(context, Method.GET, url, listener, errorListener);
    }

    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, "utf8");
        } catch (UnsupportedEncodingException var4) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }
}
