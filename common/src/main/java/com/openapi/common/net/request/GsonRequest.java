package com.openapi.common.net.request;

import android.content.Context;

import com.openapi.common.net.http.NetError;
import com.openapi.common.net.http.NetResponse;
import com.openapi.common.net.http.Response;

import java.util.Map;

/**
 * JSON 请求（GET）。解析方式作为参数传入：
 * 单个对象用 {@link #GsonRequest(Context, String, Class, Map, Response.Listener, Response.ErrorListener)}，
 * 分页列表用 {@link #infoList}。原来的 GsonInfoListRequest 合并到这里。
 */
@SuppressWarnings("unchecked")
public class GsonRequest<R> extends BaseRequest<R> {

    /** 把响应 JSON 解析成业务数据。 */
    public interface Decoder<R> {
        ApiResult<R> decode(String json, String authKey);
    }

    private final Decoder<R> decoder;

    public GsonRequest(Context context, String url, Class<R> clasz, Map<String, String> headers,
                       Response.Listener<R> listener, Response.ErrorListener errorListener) {
        this(context, url, headers, (json, key) -> DataBuilder.parserVo(clasz, json, key), listener, errorListener);
    }

    public GsonRequest(Context context, String url, Map<String, String> headers, Decoder<R> decoder,
                       Response.Listener<R> listener, Response.ErrorListener errorListener) {
        super(context, Method.GET, url, headers, listener, errorListener);
        this.decoder = decoder;
    }

    /** 分页列表（服务端返回 InfoListVo 结构）。 */
    public static <T> GsonRequest<InfoListVo<T>> infoList(Context context, String url, Class<T> clasz,
                                                          Map<String, String> headers,
                                                          Response.Listener<InfoListVo<T>> listener,
                                                          Response.ErrorListener errorListener) {
        return new GsonRequest<>(context, url, headers,
                (json, key) -> DataBuilder.parserListVo(clasz, json, key), listener, errorListener);
    }

    @Override
    protected Response<R> parseNetworkResponse(NetResponse response) {
        try {
            String json = getResponseStr(response);
            ApiResult<R> data = decoder.decode(json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new NetError.ParseError(e));
        }
    }
}
