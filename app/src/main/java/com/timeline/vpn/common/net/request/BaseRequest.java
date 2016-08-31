package com.timeline.vpn.common.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.timeline.vpn.bean.vo.JsonResult;
import com.timeline.vpn.common.exce.MyVolleyError;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.util.DeviceInfoUtils;
import com.timeline.vpn.common.util.Md5;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.common.util.cache.DiskBasedCacheEx;
import com.timeline.vpn.constant.Constants;

import java.util.Date;
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
    private String mCharset = "utf-8";

    public BaseRequest(Context context, int method, String url,
                       Map<String, String> headers, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        long time = new Date().getTime();
        mContext = context;
        if (UA_APP_SUFFIX == null) {
            UA_APP_SUFFIX = HttpUtils.getUserAgentSuffix(context);
        }
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(DeviceInfoUtils.getDeviceId(context)).append("|").append(time);
        String msg = time+Md5.encode(sb.toString());
        headers.put("User-Agent", UA_DEFAULT + UA_APP_SUFFIX+","+msg);
        if (!headers.containsKey("Referer")) {
            headers.put("Referer", Constants.DEFAULT_REFERER);
        }
        headers.put("Accept-Encoding", "gzip");
        headers.put("Accept-Language", SystemUtils.getLang(context));
        headers.put(Constants.DEVID, DeviceInfoUtils.getDeviceId(context));
        String token = PreferenceUtils.getPrefString(context, Constants.HTTP_TOKEN_KEY, null);
        if(token!=null)
            headers.put(Constants.HTTP_TOKEN_KEY, token);
        this.headers = headers;
        this.listener = listener;
        setRetryPolicy(new DefaultRetryPolicy(5000, 1, 0.5F));
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

    protected Response parserData(JsonResult data, NetworkResponse response) {
        boolean ret = HttpUtils.parserJsonResult(getContext(), data);
        if (ret) {
            return Response.success(data.getData(), getCacheEntry(response));
        } else {
            return Response.error(new MyVolleyError(data.error));
        }
    }
}
