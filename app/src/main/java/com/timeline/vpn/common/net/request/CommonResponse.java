package com.timeline.vpn.common.net.request;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.timeline.vpn.common.net.VolleyUtils;

/**
 * Created by gqli on 2016/8/17.
 */
public class CommonResponse {
    public static class ResponseErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            VolleyUtils.showVolleyError(volleyError);
            onError();
        }

        protected void onError() {
        }
    }

    public static class ResponseOkListener<T> implements Response.Listener<T> {
        @Override
        public void onResponse(T o) {

        }
    }
}
