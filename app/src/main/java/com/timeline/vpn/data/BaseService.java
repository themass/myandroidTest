package com.timeline.vpn.data;

import android.content.Context;

import com.android.volley.toolbox.RequestFuture;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.net.request.GsonInfoListRequest;
import com.timeline.vpn.common.net.request.GsonRequest;
import com.timeline.vpn.common.net.request.MultipartRequest;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.Map;

/**
 * Created by gqli on 2016/3/10.
 */
public class BaseService {
    protected Context context;
    public void setup(Context context){
        this.context=context;
    }
    public <T>InfoListVo<T> getInfoListData(String url, Class<T> t,String tag){
        try {
            RequestFuture<InfoListVo<T>> future = RequestFuture.newFuture();
            GsonInfoListRequest request = new GsonInfoListRequest(context, url, t,null, future, future);
            request.setTag(tag);
            VolleyUtils.addRequest(request);
            InfoListVo<T> result = future.get();
            return result;
        }catch (Exception e){
            LogUtil.e(e);
            return  null;
        }
    }
    public <T>InfoListVo<T> getInfoListData(String url, Map<String,String> param, Class<T> t,String tag){
        try {
            url = HttpUtils.generateGetUrl(url,param);
            RequestFuture<InfoListVo<T>> future = RequestFuture.newFuture();
            GsonInfoListRequest request = new GsonInfoListRequest(context, url, t,null, future, future);
            request.setTag(tag);
            VolleyUtils.addRequest(request);
            InfoListVo<T> result = future.get();
            return result;
        }catch (Exception e){
            LogUtil.e(e);
            return  null;
        }
    }
    public <T>void getData(String url,CommonResponse.ResponseOkListener<T> listener,CommonResponse.ResponseErrorListener errorListener,String tag,Class<T> t){
        GsonRequest request = new GsonRequest(context, url, t, null,listener,errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }
    public <T>void postData(String url,Map<String,String>param,CommonResponse.ResponseOkListener<T> listener,CommonResponse.ResponseErrorListener errorListener,String tag,Class<T> t){
        MultipartRequest request = new MultipartRequest(context, param,url,null,listener,errorListener, t);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }
    public <T>void getInfoListData(String url,CommonResponse.ResponseOkListener<T> serverListener,CommonResponse.ResponseErrorListener errorListener,String tag,Class<T> t){
        GsonInfoListRequest request = new GsonInfoListRequest(context, url, t, null,serverListener,errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }
    public <T>void getData(String url,CommonResponse.ResponseOkListener<T> serverListener,CommonResponse.ResponseErrorListener errorListener,String tag,Class<T> t,Map<String,String>param){
        url = HttpUtils.generateGetUrl(url,param);
        GsonRequest request = new GsonRequest(context, Constants.SERVERLIST_URL, t, null,serverListener,errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }
    public void cancelRequest(String tag){
        VolleyUtils.cancelRequest(tag);
    }
}
