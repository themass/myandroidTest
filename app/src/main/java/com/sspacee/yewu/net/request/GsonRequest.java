package com.sspacee.yewu.net.request;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.timeline.myapp.bean.DataBuilder;
import com.timeline.myapp.bean.vo.JsonResult;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class GsonRequest<T> extends BaseRequest<T> {
    private Class<T> clasz;

    public GsonRequest(Context context, String url, Class<T> clasz, Map<String, String> headers,
                       Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.clasz = clasz;
    }
    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            JsonResult<T> data = DataBuilder.parserVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }


}
