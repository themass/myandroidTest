package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.timeline.vpn.bean.DataBuilder;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.JsonResult;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
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
            JsonResult<InfoListVo<T>> data = DataBuilder.parserListVo(clasz, json);
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }


}
