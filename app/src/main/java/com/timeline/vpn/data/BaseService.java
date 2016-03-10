package com.timeline.vpn.data;

import android.content.Context;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.timeline.vpn.common.net.VolleyUtils;

/**
 * Created by gqli on 2016/3/10.
 */
public class BaseService {
    protected Context context;
    protected  Response.ErrorListener commonErrorListener = new ResponseErrorListener();
    public void setup(Context context){
        this.context=context;
    }
    public static class ResponseErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            VolleyUtils.showVolleyError(volleyError);
            volleyError.getCause().printStackTrace();
            onError();
        }
        protected void onError(){ }
    }
}
