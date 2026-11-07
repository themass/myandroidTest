package com.openapi.core.net.request;

import com.openapi.core.net.NetError;
import com.openapi.core.net.NetQueue;
import com.openapi.core.net.Response;

/**
 * Created by dengt on 2016/8/17.
 */
public class CommonResponse {
    public static class ResponseErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(NetError error) {
            NetQueue.showVolleyError(error);
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
