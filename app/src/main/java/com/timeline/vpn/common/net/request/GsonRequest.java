package com.timeline.vpn.common.net.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;
import com.timeline.vpn.api.bean.JsonResult;
import com.timeline.vpn.common.exce.JsonResultException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class GsonRequest<T> extends BaseRequest<T> {

    private final Gson gson = new Gson();
    private  Class<T>  clasz;
    private String mCharset = "utf-8";
    public GsonRequest(Context context, String url, Class<T> clasz, Map<String, String> headers,
                       Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clasz = clasz;
    }
    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            Type typeOfT = type(JsonResult.class,clasz);
            JsonResult<T> data = gson.fromJson(json, typeOfT);
            boolean ret = parserJsonResult(data);
            if(ret) {
                return Response.success(data.getData(), getCacheEntry(response));
            }else{
                return Response.error(new ParseError(new JsonResultException(data)));
            }
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
    public static ParameterizedType type(final Class raw, final Type... args) {
        return new ParameterizedType() {
            public Type getRawType() {
                return raw;
            }

            public Type[] getActualTypeArguments() {
                return args;
            }

            public Type getOwnerType() {
                return null;
            }
        };
    }
    public static boolean parserJsonResult(JsonResult<?> result){
        return true;
    }

}
