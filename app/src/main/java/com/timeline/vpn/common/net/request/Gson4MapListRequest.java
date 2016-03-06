package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;

import java.util.List;
import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class Gson4MapListRequest extends GsonRequest<List<Map<String, Object>>> {

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url           URL of the request to make
     * @param headers       Map of request headers
     * @param listener
     * @param errorListener
     */
    public Gson4MapListRequest(Context context, String url, Map<String, String> headers, Response.Listener<List<Map<String, Object>>> listener, Response.ErrorListener errorListener) {
        super(context, url, null, headers, listener, errorListener);
    }


    @Override
    protected Response<List<Map<String, Object>>> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
//            if (shouldCache()) {
//                SimpleDiskCacheUtils.getSimpleDiskCache(getContext()).put(getUrl(), json);
//            }
            String realJson = dealResultJson(json);
            List<Map<String, Object>> list = GsonUtils.getMapList(getGson(), realJson);
            dealResultMapList(list);
            return Response.success(list, getCacheEntry(response));
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

    protected String dealResultJson(String json) {
        String resultJsonString = json;
        //searchList json头处理
        if (json.startsWith("_ntes_stocksearch_callback")) {
            resultJsonString = json.substring(27, json.length() - 1);
        }
        return resultJsonString;
    }

    protected void dealResultMapList(List<Map<String, Object>> list) {

    }
}
