package com.openapi.commons.yewu.net.request;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.openapi.commons.yewu.net.VolleyUtils;

/**
 * Created by openapi on 2016/8/17.
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
        private final Object param;

        public ResponseOkListener() {
            param = null;
        }

        public ResponseOkListener(Object param) {
            this.param = param;
        }

        @Override
        public void onResponse(T o) {

        }

        public Object getParam() {
            return param;
        }
    }
}
