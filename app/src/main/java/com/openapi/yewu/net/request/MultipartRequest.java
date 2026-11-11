package com.openapi.yewu.net.request;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.bean.DataBuilder;
import com.openapi.myapp.bean.vo.JsonResult;

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
        this(context, url, headers, file, name, null, listener, errorListener, clasz);
    }

    /**
     * 带文本参数的文件上传。
     * <p>
     * 原来的 file 构造器把 param 留成 null，只能传文件本身。上报问题时要额外带一段用户填的
     * 问题描述（服务端 bug.json 的 label），否则收到的只有堆栈、不知道用户实际遇到了什么。
     */
    public MultipartRequest(Context context,
                            String url, Map<String, String> headers, List<File> file, String name,
                            Map<String, String> param, Listener<T> listener,
                            ErrorListener errorListener, Class<T> clasz) {
        super(context, Method.POST, url, headers, listener, errorListener);
        this.file = file;
        this.name = name;
        this.param = param;
        this.clasz = clasz;
    }

    @Override
    public Map<String, String> getParams() throws AuthFailureError {
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
