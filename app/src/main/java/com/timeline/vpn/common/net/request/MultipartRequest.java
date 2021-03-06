package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.timeline.vpn.bean.DataBuilder;
import com.timeline.vpn.bean.vo.JsonResult;
import com.timeline.vpn.common.util.LogUtil;

import java.util.Map;

public class MultipartRequest<T> extends BaseRequest<T> {
    private Map<String, String> param;
    private   Class<T>  clasz;
    public  MultipartRequest(Context context, Map<String, String> param,
                            String url, Map<String, String> headers, Listener<T> listener,
                            ErrorListener errorListener,Class<T>  clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.param = param;
        this.clasz = clasz;
    }
    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        return param;
    }

    @Override
    protected Map<String, String> getPostParams() throws AuthFailureError {
        return param;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            LogUtil.i(json);
            JsonResult<T> data = DataBuilder.parserVo(clasz, json);
            return parserData(data,response);
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

}
