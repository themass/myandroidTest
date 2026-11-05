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
public class GsonInfoListRequest<T> extends BaseRequest<InfoListVo<T>> {
    private Class<T> clasz;

    public GsonInfoListRequest(Context context, String url, Class<T> clasz, Map<String, String> headers,
                               Response.Listener<InfoListVo<T>> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clasz = clasz;
    }

    @Override
    protected Response<InfoListVo<T>> parseNetworkResponse(NetResponse response) {
        try {
            String json = getResponseStr(response);
            JsonResult<InfoListVo<T>> data = DataBuilder.parserListVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new NetError.ParseError(e));
        }
    }
}
