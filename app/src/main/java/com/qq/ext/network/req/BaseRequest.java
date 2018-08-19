package com.qq.ext.network.req;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.qq.Constants;
import com.qq.ext.except.MVolleyError;
import com.qq.ext.util.DeviceInfoUtils;
import com.qq.ext.util.Md5;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.cache.DiskBasedCacheEx;
import com.qq.vpn.domain.res.JsonResult;
import com.qq.vpn.support.StaticDataUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BaseRequest<T> extends Request<T> {

    public final Context mContext;
    public final Map<String, String> headers;
    public final Response.Listener<T> listener;
    private String key;
    public BaseRequest(Context context, int method, String url,
                       Map<String, String> headers, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        long time = new Date().getTime();
        mContext = context;
        if (headers == null) {
            headers = new HashMap<>();
        }
        String sb = DeviceInfoUtils.getDeviceId(context) + "|" + time;
        String msg = time + Md5.encode(sb);
        String ua = Constants.NetWork.UA_DEFAULT + Constants.NetWork.UA_APP_SUFFIX +",channel="+Constants.NetWork.uc+",cpu=" + SystemUtils.getCpuType() + ",IE" + msg;
        String loc = "lon:" + StaticDataUtil.get(Constants.LON, Double.class) + ";lat:" + StaticDataUtil.get(Constants.LAT, Double.class);
        this.key = ua.substring(ua.length() - 16, ua.length());
        headers.put(Constants.USER_AGENT, ua);
        if (!headers.containsKey(Constants.REFERER)) {
            headers.put(Constants.REFERER, Constants.DEFAULT_REFERER);
        }
        headers.put("Accept-Encoding", "gzip");
        headers.put("Accept-Language", SystemUtils.getLang(context));
        headers.put(Constants.DEVID, DeviceInfoUtils.getDeviceId(context));
        String token = PreferenceUtils.getPrefString(context, Constants.HTTP_TOKEN_KEY, null);
        if (token != null)
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

    protected final String getResponseStr(NetworkResponse response) throws Exception {
        return new String(response.data, Constants.NetWork.CHARSET);
    }

    /**
     * 设置默认编码
     *
     * @param charset
     * @return //
     */
    protected void setCharset(String charset) {
        Constants.NetWork.CHARSET = charset;
    }

    private boolean parserJsonResult(Context context, int result) {
        switch (result) {
            case Constants.HTTP_SUCCESS:
                return true;
            default:
                return false;
        }
    }

    protected Response parserData(JsonResult data, NetworkResponse response) {
        boolean ret = parserJsonResult(getContext(), data.errno);
        if (ret) {
            return Response.success(data.getData(), getCacheEntry(response));
        } else {
            return Response.error(new MVolleyError(data.error));
        }
    }

    public String getAuthkey() {
        return key;
    }

    public void setAuthkey(String authkey) {
        this.key = authkey;
    }
}
