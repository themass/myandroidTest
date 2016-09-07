package com.timeline.vpn.data;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.toolbox.RequestFuture;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.net.request.GsonInfoListRequest;
import com.timeline.vpn.common.net.request.GsonRequest;
import com.timeline.vpn.common.net.request.MultipartRequest;
import com.timeline.vpn.common.util.GsonUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Created by gqli on 2016/3/10.
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

    public <T> InfoListVo<T> getInfoListData(String url, Map<String, String> param, Class<T> t, String tag) throws Exception {
        url = HttpUtils.generateGetUrl(url, param);
        RequestFuture<InfoListVo<T>> future = RequestFuture.newFuture();
        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null, future, future);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
        return future.get();
    }

    public <T> T getData(String url, Class<T> t, String tag) throws Exception {
        RequestFuture<T> future = RequestFuture.newFuture();
        GsonRequest request = new GsonRequest<T>(context, url, t, null, future, future);
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
        Map map = null;
        if(param!=null)
            map = GsonUtils.getMap(GsonUtils.getInstance().toJson(param));
        MultipartRequest request = new MultipartRequest(context, map, url, null, listener, errorListener, t);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    public <T> void getInfoListData(String url, CommonResponse.ResponseOkListener<T> serverListener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t) {
        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null, serverListener, errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    public <T> void getData(String url, CommonResponse.ResponseOkListener<T> serverListener, CommonResponse.ResponseErrorListener errorListener, String tag, Class<T> t, Map<String, String> param) {
        url = HttpUtils.generateGetUrl(url, param);
        GsonRequest request = new GsonRequest(context, Constants.API_SERVERLIST_URL, t, null, serverListener, errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    private void parseError(Exception e) {
        if (e instanceof ExecutionException) {
            Toast.makeText(context, e.getCause().getMessage(), Toast.LENGTH_SHORT).show();
        } else {
            LogUtil.e(e);
        }
    }

    public void cancelRequest(String tag) {
        VolleyUtils.cancelRequest(tag);
    }
}
