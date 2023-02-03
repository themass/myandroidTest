package com.sspacee.yewu.net.request;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.sspacee.common.util.LogUtil;
import com.ks.myapp.bean.DataBuilder;
import com.ks.myapp.bean.vo.JsonResult;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MultipartRequest<T> extends BaseRequest<T> {
    private List<File> file;
    private Map<String, String> param;
    private Class<T> clasz;
    private String name;

    public MultipartRequest(Context context, Map<String, String> param,
                            String url, Map<String, String> headers, Listener<T> listener,
                            ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.param = param;
        this.clasz = clasz;
    }

    public MultipartRequest(Context context,
                            String url, Map<String, String> headers, List<File> file, String name, Listener<T> listener,
                            ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.file = file;
        this.name = name;
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
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = getResponseStr(response);
            LogUtil.i(json);
            JsonResult<T> data = DataBuilder.parserVo(clasz, json, getAuthkey());
            return parserData(data, response);
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

}
