package com.timeline.vpn.data;

import com.android.volley.Response;
import com.timeline.vpn.api.bean.ServerVo;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.GsonRequest;
import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/10.
 */
public class IndexService extends BaseService{
    public void getHost(Response.Listener serverListener,ResponseErrorListener serverErrorListener){
        GsonRequest request = new GsonRequest(context, Constants.SERVERLIST_URL, ServerVo.class, HttpUtils.getHeader(),serverListener,serverErrorListener);
        VolleyUtils.addRequest(request);
    }
//    public void getRecommendList();
}
