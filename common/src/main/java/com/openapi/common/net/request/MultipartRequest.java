package com.openapi.common.net.request;

import android.content.Context;

import com.openapi.common.net.http.NetError;
import com.openapi.common.net.http.NetResponse;
import com.openapi.common.net.http.Response;

import java.io.File;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "deprecation"})
public class MultipartRequest<T> extends BaseRequest<T> {
    private List<File> file;
    private Map<String, String> param;
    private Class<T> clasz;
    private String name;

    public MultipartRequest(Context context, Map<String, String> param,
                            String url, Map<String, String> headers, Response.Listener<T> listener,
                            Response.ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.param = param;
        this.clasz = clasz;
    }

    public MultipartRequest(Context context,
                            String url, Map<String, String> headers, List<File> file, String name, Response.Listener<T> listener,
                            Response.ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.file = file;
        this.name = name;
        this.clasz = clasz;
    }

    public MultipartRequest(Context context,
                            String url, Map<String, String> headers, List<File> file, Map<String, String> param, String name, Response.Listener<T> listener,
                            Response.ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.file = file;
        this.name = name;
        this.param = param;
        this.clasz = clasz;
    }

    @Override
    public Map<String, String> getParams() {
        return param;
    }

    @Override
    public List<File> getFiles() {
        return file;
    }

    @Override
    public String getFileField() {
        return name;
    }

    public List<File> getFile() {
        return file;
    }

    public void setFile(List<File> file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetResponse response) {
        try {
            String json = getResponseStr(response);
            ApiResult<T> data = DataBuilder.parserVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new NetError.ParseError(e));
        }
    }
}
