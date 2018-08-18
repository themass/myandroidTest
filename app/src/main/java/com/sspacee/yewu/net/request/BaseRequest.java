package com.sspacee.yewu.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.sspacee.common.exce.MyVolleyError;
import com.sspacee.common.util.DeviceInfoUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.Md5;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.cache.DiskBasedCacheEx;
import com.sspacee.yewu.net.HttpUtils;
import com.timeline.myapp.bean.vo.JsonResult;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.data.UserLoginUtil;

import java.net.URL;
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
    private String authkey;
    private String mCharset = "utf-8";
    private static String uc = null;

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
            headers = new HashMap<>();
        }
        if(!StringUtils.hasText(uc)){
            uc = DeviceInfoUtils.getMetaData(context, "UMENG_CHANNEL");
        }
        String sb = DeviceInfoUtils.getDeviceId(context) + "|" + time;
        String msg = time + Md5.encode(sb);
        String ua = UA_DEFAULT + UA_APP_SUFFIX + ",channel="+uc+",NETTYPE="+Constants.VPNB+","+",cpu=" + SystemUtils.getCpuType() + ",IE" + msg;
        String loc = "lon:" + StaticDataUtil.get(Constants.LON, Double.class) + ";lat:" + StaticDataUtil.get(Constants.LAT, Double.class);
        this.authkey = ua.substring(ua.length() - 16, ua.length());
        headers.put("Loc", loc);
        headers.put("User-Agent", ua);
        if (!headers.containsKey("Referer")) {
            headers.put("Referer", Constants.DEFAULT_REFERER);
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

    protected final String getResponseStr(NetworkResponse response) throws Exception {
        String charset = !TextUtils.isEmpty(mCharset) ? mCharset : HttpHeaderParser.parseCharset(response.headers);
        return new String(response.data, charset);
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

    private boolean parserJsonResult(Context context, int result) {
        switch (result) {
            case Constants.HTTP_SUCCESS:
                return true;
            case Constants.HTTP_SUCCESS_CLEAR:
                UserLoginUtil.logout(context);
                return true;
            default:
                return false;
        }
    }

    protected Response parserData(JsonResult data, NetworkResponse response) {

        try {
            URL url = new URL(getUrl());
            if (data.ip != null && StaticDataUtil.get(url.getHost(), String.class) == null) {
                String ip = data.ip.split(";")[0].split(":")[0];
                StaticDataUtil.add(url.getHost(), ip);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        boolean ret = parserJsonResult(getContext(), data.errno);
        if (ret) {
            return Response.success(data.getData(), getCacheEntry(response));
        } else {
            return Response.error(new MyVolleyError(data.error));
        }
    }

    public String getAuthkey() {
        return authkey;
    }

    public void setAuthkey(String authkey) {
        this.authkey = authkey;
    }
}
