package com.timeline.vpn.common.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;

import java.util.Map;

public class Gson4MapMultipartRequest extends MultipartRequest<Map<String, Object>> {
    private final Gson gson = new Gson();
    private String mCharset = "utf-8";

    public Gson4MapMultipartRequest(Context context, Map<String, String> param,
                                    String url, Map<String, String> headers,
                                    Listener<Map<String, Object>> listener, ErrorListener errorListener) {
        super(context, param, url, headers, listener, errorListener);
    }

    @Override
    protected Response<Map<String, Object>> parseNetworkResponse(
            NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            String realJson = dealResultJson(json);
            Map<String, Object> map = GsonUtils.getMap(gson, realJson);

            return Response.success(map, getCacheEntry(response));
        } catch (Exception e) {
            e.printStackTrace();
            return Response.error(new ParseError(e));
        }
    }

    protected final String getResponseStr(NetworkResponse response) {
        try {
            String charset = !TextUtils.isEmpty(mCharset) ? mCharset : HttpHeaderParser.parseCharset(response.headers);
            return new String(response.data, charset);
        } catch (Exception e) {
            return null;
        }
    }

    protected String dealResultJson(String json) {
        String resultJsonString = json;
        if (json.startsWith("_ntes_quote_callback")) {
            resultJsonString = json.substring(21, json.length() - 2);
        }
        return resultJsonString;
    }

}
