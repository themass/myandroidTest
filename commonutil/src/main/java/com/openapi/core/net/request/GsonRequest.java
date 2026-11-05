package com.openapi.core.net.request;

import android.content.Context;

import com.openapi.core.net.NetError;
import com.openapi.core.net.NetResponse;
import com.openapi.core.net.Response;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
@SuppressWarnings("unchecked")
public class GsonRequest<T> extends BaseRequest<T> {
    private Class<T> clasz;

    public GsonRequest(Context context, String url, Class<T> clasz, Map<String, String> headers,
                       Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clasz = clasz;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetResponse response) {
        try {
            String json = getResponseStr(response);
            JsonResult<T> data = DataBuilder.parserVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new NetError.ParseError(e));
        }
    }
}
