package com.openapi.common.net.http;

import com.openapi.common.net.BaseServiceAsyncKt;

import android.content.Context;

import com.openapi.common.util.lang.BeanUtil;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.net.request.CommonResponse;
import com.openapi.common.net.request.GsonRequest;
import com.openapi.common.net.request.MultipartRequest;
import com.openapi.common.net.request.StringRequest;
import com.openapi.common.net.request.InfoListVo;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

/**
 * Created by openapi on 2016/3/10.
 */
@SuppressWarnings("unchecked")
public class BaseService {
    /** 公开给同包外的扩展函数（BaseServiceAwait.kt）使用。 */
    public Context context;

    public void setup(Context context) {
        this.context = context;
    }

    /** 分页列表（非阻塞，结果通过 CompletableFuture 交给调用方）。 */
    public <T> CompletableFuture<InfoListVo<T>> getInfoListDataAsync(String url, Class<T> t, String tag) {
        return BaseServiceAsyncKt.infoListAsync(this, url, t, tag);
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
    /** 单个对象（非阻塞）。 */
    public <T> CompletableFuture<T> getDataAsync(String url, Class<T> t, String tag) {
        return BaseServiceAsyncKt.getAsync(this, url, t, tag);
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
