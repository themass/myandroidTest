package com.timeline.vpn.common.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class GsonRequest<T> extends BaseRequest<T> {

    private final Gson gson = new Gson();
    private final Class<T> clazz;
    private String mCharset = "utf-8";

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url     URL of the request to make
     * @param clazz   Relevant class object, for Gson's reflection
     * @param headers Map of request headers
     */
    public GsonRequest(Context context, String url, Class<T> clazz, Map<String, String> headers,
                       Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clazz = clazz;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            return Response.success(gson.fromJson(json, clazz), getCacheEntry(response));
        } catch (Exception e) {
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

    /**
     * 设置默认编码
     *
     * @param charset
     * @return //
     */
    protected void setCharset(String charset) {
        mCharset = charset;
    }

    protected Gson getGson() {
        return this.gson;
    }

}
