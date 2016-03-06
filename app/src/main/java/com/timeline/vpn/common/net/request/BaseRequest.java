package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.util.cache.DiskBasedCacheEx;
import com.timeline.vpn.constant.Constants;

import java.util.HashMap;
import java.util.Map;

public class BaseRequest<T> extends Request<T> {

    private static String UA_DEFAULT = null;
    private static String UA_APP_SUFFIX = null;

    static {
        UA_DEFAULT = System.getProperty("http.agent", "");
    }

    private final Context mContext;
    private final Map<String, String> headers;
    private final Response.Listener<T> listener;

    public BaseRequest(Context context, int method, String url,
                       Map<String, String> headers, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        mContext = context;
        if (UA_APP_SUFFIX == null) {
            UA_APP_SUFFIX = HttpUtils.getUserAgentSuffix(context);
        }
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        headers.put("User-Agent", UA_DEFAULT + UA_APP_SUFFIX);
        if (!headers.containsKey("Referer")) {
            headers.put("Referer", Constants.DEFAULT_REFERER);
        }
        headers.put("Accept-Encoding", "gzip");
        this.headers = headers;

        this.listener = listener;
        setRetryPolicy(new DefaultRetryPolicy(2000, 2, 0.5F));
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    protected void deliverResponse(T response) {
        if (listener != null)
            listener.onResponse(response);
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        return null;

    }

    protected Cache.Entry getCacheEntry(NetworkResponse response) {
        if (response.data != null
                && response.data.length > DiskBasedCacheEx.MAX_ENTRY_SIZE) {
            // disable cache for this url
            return null;
        } else {
            return HttpHeaderParser.parseCacheHeaders(response);
        }
    }

    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        return super.getParams();
    }

    @Override
    public String getBodyContentType() {
        return super.getBodyContentType();
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        return super.getBody();
    }

}
