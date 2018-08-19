package com.qq.ext.network.req;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.qq.BeanBuilder;
import com.qq.vpn.domain.res.InfoListVo;
import com.qq.vpn.domain.res.JsonResult;

import java.util.Map;

/**
 * Created by dengt on 14-03-12.
 */
public class GsonInfoListRequest<T> extends BaseRequest<InfoListVo<T>> {
    private Class<T> clasz;

    public GsonInfoListRequest(Context context, String url, Class<T> clasz, Map<String, String> headers,
                               Response.Listener<InfoListVo<T>> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clasz = clasz;
    }

    @Override
    protected Response<InfoListVo<T>> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            JsonResult<InfoListVo<T>> data = BeanBuilder.parserListVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }


}
