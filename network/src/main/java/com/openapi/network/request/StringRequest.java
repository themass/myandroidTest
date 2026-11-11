package com.openapi.network.request;

import android.content.Context;

import com.openapi.network.http.NetError;
import com.openapi.network.http.NetResponse;
import com.openapi.network.http.Response;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class StringRequest extends BaseRequest<String> {
    public StringRequest(Context context, int method, String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(context, method, url, null, listener, errorListener);
    }

    public StringRequest(Context context, String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        this(context, Method.GET, url, listener, errorListener);
    }

    @Override
    protected Response<String> parseNetworkResponse(NetResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, "utf8");
        } catch (UnsupportedEncodingException var4) {
            parsed = new String(response.data);
        }
        return Response.success(parsed);
    }
}
