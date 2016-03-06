package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class Gson4MapRequest extends GsonRequest<Map<String, Object>> {

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url           URL of the request to make
     * @param headers       Map of request headers
     * @param listener
     * @param errorListener
     */
    public Gson4MapRequest(Context context, String url, Map<String, String> headers,
                           Response.Listener<Map<String, Object>> listener, Response.ErrorListener errorListener) {
        super(context, url, null, headers, listener, errorListener);
    }

    @Override
    protected Response<Map<String, Object>> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
//            if (shouldCache()) {
//                SimpleDiskCacheUtils.getSimpleDiskCache(getContext()).put(getUrl(), json);
//            }
            String realJson = dealResultJson(json);
            Map<String, Object> map = GsonUtils.getMap(getGson(), realJson);

            return Response.success(map, getCacheEntry(response));
        } catch (Exception e) {
            e.printStackTrace();
            return Response.error(new ParseError(e));
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
