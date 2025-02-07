package com.openai.net.request;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.openai.commonevent.UserNeedLogin;
import com.openai.commonutil.DeviceInfoUtils;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.Md5;
import com.openai.commonutil.StaticDataUtil;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.cache.DiskBasedCacheEx;
import com.openai.net.HttpUtils;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.StringUtils;
import com.openai.net.MyVolleyError;
import com.openai.net.request.JsonResult;
import com.openai.CommonConstants;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BaseRequest<T> extends Request<T> {

    private static String UA_DEFAULT = null;
    private static String UA_APP_SUFFIX = null;
    public static String DEVID = null;

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
        if (!StringUtils.hasText(uc)) {
            uc = DeviceInfoUtils.getMetaData(context, "UMENG_CHANNEL");
        }
        String sb = null;
        String fileTxt = DeviceInfoUtils.NULL;
        if (DEVID == null) {
            sb = DeviceInfoUtils.getDeviceId(context);
            if (!DeviceInfoUtils.NULL.equals(sb)) {
                DEVID = sb;
//                fileTxt = FileUtilleUtils.getContextId(context,sb);
            }
        } else {
            sb = DEVID;
//            fileTxt = FileUtils.getContextId(context,DEVID);
        }
//        LogUtil.i("devid="+sb+"---"+ FileUtils.getContextId(context,sb));
        sb = sb + "|" + time;
        String msg = time + Md5.encode(sb);
        String ua = UA_DEFAULT + UA_APP_SUFFIX + ",channel=" + uc + ",cpu=" + SystemUtils.getCpuType() + ",Webkit/" + fileTxt + ",IE" + msg;
        String loc = "lon:" + StaticDataUtil.get(CommonConstants.LON, Double.class) + ";lat:" + StaticDataUtil.get(CommonConstants.LAT, Double.class);
        this.authkey = ua.substring(ua.length() - 16, ua.length());
        headers.put("Loc", loc);
        headers.put("User-Agent", ua);
        if (!headers.containsKey("Referer")) {
            headers.put("Referer", CommonConstants.DEFAULT_REFERER);
        }
        headers.put("Accept-Encoding", "gzip");
        headers.put("Accept-Language", SystemUtils.getLang(context));
        headers.put("userLang", SystemUtils.getUserLang(context));
        headers.put(CommonConstants.DEVID, DEVID);
        String token = PreferenceUtils.getPrefString(context, CommonConstants.HTTP_TOKEN_KEY, null);
        if (token != null)
            headers.put(CommonConstants.HTTP_TOKEN_KEY, token);
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
            case CommonConstants.HTTP_SUCCESS:
                return true;
            case CommonConstants.HTTP_SUCCESS_CLEAR:
                EventBusUtil.getEventBus().post(new UserNeedLogin());
                return true;
            case CommonConstants.HTTP_LOGIN:
                EventBusUtil.getEventBus().post(new UserNeedLogin());
                return false;
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
