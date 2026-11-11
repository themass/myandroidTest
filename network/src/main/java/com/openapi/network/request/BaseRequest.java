package com.openapi.network.request;

import android.content.Context;
import android.text.TextUtils;

import com.openapi.common.util.android.DeviceInfoUtils;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.lang.Md5;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.network.http.HttpUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.network.http.NetError;
import com.openapi.network.http.ApiRequest;
import com.openapi.network.http.NetResponse;
import com.openapi.network.http.Response;
import com.openapi.network.request.ApiResult;
import com.openapi.network.http.NetConfig;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BaseRequest<T> extends ApiRequest<T> {

    private static String UA_DEFAULT = null;
    private static String UA_APP_SUFFIX = null;
    public static String DEVID = null;

    static {
        UA_DEFAULT = System.getProperty("http.agent", "");
    }

    private final Context mContext;
    private final Map<String, String> headers;
    private String authkey;
    private String mCharset = "utf-8";
    private static String uc = null;

    public BaseRequest(Context context, int method, String url,
                       Map<String, String> headers, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, listener, errorListener);
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
        String loc = "lon:" + StaticDataUtil.get("lon", Double.class) + ";lat:" + StaticDataUtil.get("lat", Double.class);
        this.authkey = ua.substring(ua.length() - 16, ua.length());
        headers.put("Loc", loc);
        headers.put("User-Agent", ua);
        if (!headers.containsKey("Referer")) {
            headers.put("Referer", NetConfig.defaultReferer());
        }
        headers.put("Accept-Encoding", "gzip");
        headers.put("Accept-Language", SystemUtils.getLang(context));
        Locale currentLocale = Locale.getDefault();
        try {
            headers.put("userLang", URLEncoder.encode(currentLocale.getCountry() + ";" + currentLocale.getLanguage()+";" +currentLocale.getDisplayName(), "UTF-8"));
        }catch (Exception e){
            headers.put("userLang", currentLocale.getCountry() + ";" + currentLocale.getLanguage());
        }
        headers.put(NetConfig.deviceHeader(), DEVID);
        String token = PreferenceUtils.getPrefString(context, NetConfig.tokenHeader(), null);
        if (token != null)
            headers.put(NetConfig.tokenHeader(), token);
        this.headers = headers;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers != null ? headers : super.getHeaders();
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetResponse response) {
        return null;

    }

    protected final String getResponseStr(NetResponse response) throws Exception {
        String charset = !TextUtils.isEmpty(mCharset) ? mCharset : "utf-8";
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
        if (result == NetConfig.CODE_SUCCESS) {
            return true;
        }
        if (result == NetConfig.CODE_SUCCESS_CLEAR_LOGIN) {
            NetConfig.notifyAuthRequired(result);
            return true;
        }
        if (result == NetConfig.CODE_LOGIN_REQUIRED) {
            NetConfig.notifyAuthRequired(result);
            return false;
        }
        return false;
    }

    protected Response parserData(ApiResult data, NetResponse response) {
        LogUtil.i( "收到服务器响应: " + GsonUtils.getInstance().toJson( data));
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
            return Response.success(data.getData());
        } else {
            return Response.error(new NetError.BusinessError(data.error));
        }
    }

    public String getAuthkey() {
        return authkey;
    }

    public void setAuthkey(String authkey) {
        this.authkey = authkey;
    }
}
