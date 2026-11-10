package com.openapi.core.net;

import android.content.Context;

import com.openapi.core.util.BeanUtil;
import com.openapi.core.util.LogUtil;
import com.openapi.core.net.request.CommonResponse;
import com.openapi.core.net.request.GsonRequest;
import com.openapi.core.net.request.MultipartRequest;
import com.openapi.core.net.request.StringRequest;
import com.openapi.core.net.request.InfoListVo;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created by openapi on 2016/3/10.
 */
@SuppressWarnings("unchecked")
public class BaseService {
    protected Context context;

    public void setup(Context context) {
        this.context = context;
    }

    public <T> InfoListVo<T> getInfoListData(String url, Class<T> t, String tag) throws Exception {
        SyncFuture<InfoListVo<T>> future = SyncFuture.newFuture();
        GsonRequest<InfoListVo<T>> request = GsonRequest.infoList(context, url, t, null, future, future);
        request.setTag(tag);
        HttpClient.addRequest(request);
        return future.get();
    }

    //
//    public <T> InfoListVo<T> getInfoListData(String url, Map<String, String> param, Class<T> t, String tag) throws Exception {
//        url = HttpUtils.generateGetUrl(url, param);
//        SyncFuture<InfoListVo<T>> future = SyncFuture.newFuture();
//        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null, future, future);
//        request.setTag(tag);
//        HttpClient.addRequest(request);
//        return future.get();
//    }
//
    public <T> T getData(String url, Class<T> t, String tag) throws Exception {
        SyncFuture<T> future = SyncFuture.newFuture();
        GsonRequest request = new GsonRequest<>(context, url, t, null, future, future);
        request.setTag(tag);
        HttpClient.addRequest(request);
        return future.get();
    }

    public String getStringData(String url, String tag) throws Exception {
        SyncFuture<String> future = SyncFuture.newFuture();
        StringRequest request = new StringRequest(context, ApiRequest.Method.GET, url, future, future);
        request.setTag(tag);
        HttpClient.addRequest(request);
        return future.get();
    }

    public <T> void getData(String url, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t) {
        GsonRequest request = new GsonRequest(context, url, t, null, listener, errorListener);
        request.setTag(tag);
        HttpClient.addRequest(request);
    }

    public <T> void postData(String url, Object param, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t) {
        Map<String, String> map = null;
        if (param != null) {
            map = BeanUtil.transBean2Map(param);
        }
        MultipartRequest request = new MultipartRequest(context, map, url, null, listener, errorListener, t);
        request.setTag(tag);
        HttpClient.addRequest(request);
    }

    public <T> void postData(String url, List<File> file, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String name, String tag, Class<T> t) {
        MultipartRequest request = new MultipartRequest(context, url, null, file, name, listener, errorListener, t);
        request.setTag(tag);
        HttpClient.addRequest(request);
    }
    public <T> void postData(String url, Object param, List<File> file, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String name, String tag, Class<T> t) {
        Map<String, String> map = null;
        if (param != null) {
            map = BeanUtil.transBean2Map(param);
        }
        MultipartRequest request = new MultipartRequest(context, url, null, file, map, name, listener, errorListener, t);
        request.setTag(tag);
        HttpClient.addRequest(request);
    }
    public void cancelRequest(String tag) {
        HttpClient.cancelRequest(tag);
    }
}
