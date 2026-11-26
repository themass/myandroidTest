package com.openapi.common.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.openapi.common.util.android.DeviceInfoUtils;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.lang.Md5;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.net.http.HttpUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.net.http.NetError;
import com.openapi.common.net.http.ApiRequest;
import com.openapi.common.net.http.NetResponse;
import com.openapi.common.net.http.Response;
import com.openapi.common.net.request.ApiResult;
import com.openapi.common.net.http.NetConfig;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BaseRequest<T> extends ApiRequest<T> {

    private final Context mContext;
    private final Map<String, String> headers;
    private String authkey;
    private String mCharset = "utf-8";

    public BaseRequest(Context context, int method, String url,
                       Map<String, String> headers, Response.Listener<T> listener,
                       Response.ErrorListener errorListener) {
        super(method, url, listener, errorListener);
        long time = new Date().getTime();
        mContext = context;
        if (headers == null) {
            headers = new HashMap<>();
        }
        // UA/设备号的生成收进 UserAgent（A40）：封面那条 Glide 链要用同一份，
        // 密钥取 UA 末 16 位，两处各写一份迟早对不上。
        String ua = UserAgent.build(context);
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
        // 值为 null 的头在 HttpClient 里会被跳过，等于没发；Devid 缺失服务端直接判鉴权失败，
        // 所以这里绝不能把 null 放进去（UserAgent.deviceId 已保证非空）。
        String devid = UserAgent.deviceId(context);
        if (StringUtils.hasText(devid)) {
            headers.put(NetConfig.deviceHeader(), devid);
        }
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
            return Response.error(new NetError.BusinessError(data.errno, data.error));
        }
    }

    public String getAuthkey() {
        return authkey;
    }

    public void setAuthkey(String authkey) {
        this.authkey = authkey;
    }
}
