package com.openapi.ks.myapp.data;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.toolbox.RequestFuture;
import com.openapi.commons.common.util.BeanUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.net.VolleyUtils;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.commons.yewu.net.request.GsonInfoListRequest;
import com.openapi.commons.yewu.net.request.GsonRequest;
import com.openapi.commons.yewu.net.request.MultipartRequest;
import com.openapi.commons.yewu.net.request.StringRequest;
import com.openapi.ks.myapp.bean.vo.InfoListVo;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created by openapi on 2016/3/10.
 */
public class BaseService {
    protected Context context;

    public void setup(Context context) {
        this.context = context;
    }

    public <T> InfoListVo<T> getInfoListData(String url, Class<T> t, String tag) throws Exception {
        RequestFuture<InfoListVo<T>> future = RequestFuture.newFuture();
        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null, future, future);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
        return future.get();
    }

    //
//    public <T> InfoListVo<T> getInfoListData(String url, Map<String, String> param, Class<T> t, String tag) throws Exception {
//        url = HttpUtils.generateGetUrl(url, param);
//        RequestFuture<InfoListVo<T>> future = RequestFuture.newFuture();
//        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null, future, future);
//        request.setTag(tag);
//        VolleyUtils.addRequest(request);
//        return future.get();
//    }
//
    public <T> T getData(String url, Class<T> t, String tag) throws Exception {
        RequestFuture<T> future = RequestFuture.newFuture();
        GsonRequest request = new GsonRequest<>(context, url, t, null, future, future);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
        return future.get();
    }

    public String getStringData(String url, String tag) throws Exception {
        RequestFuture<String> future = RequestFuture.newFuture();
        StringRequest request = new StringRequest(context, Request.Method.GET, url, future, future);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
        return future.get();
    }

    public <T> void getData(String url, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t) {
        GsonRequest request = new GsonRequest(context, url, t, null, listener, errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    public <T> void postData(String url, Object param, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t) {
        Map<String, String> map = null;
        if (param != null) {
            map = BeanUtil.transBean2Map(param);
            LogUtil.i(map);
        }
        MultipartRequest request = new MultipartRequest(context, map, url, null, listener, errorListener, t);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    public <T> void postData(String url, List<File> file, CommonResponse.ResponseOkListener<T> listener, CommonResponse.ResponseErrorListener errorListener, String name, String tag, Class<T> t) {
        MultipartRequest request = new MultipartRequest(context, url, null, file, name, listener, errorListener, t);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    public void cancelRequest(String tag) {
        VolleyUtils.cancelRequest(tag);
    }
}
