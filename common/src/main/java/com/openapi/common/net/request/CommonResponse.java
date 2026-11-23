package com.openapi.common.net.request;

import com.openapi.common.net.http.NetError;
import com.openapi.common.net.http.HttpClient;
import com.openapi.common.net.http.Response;

/**
 * Created by dengt on 2016/8/17.
 */
public class CommonResponse {
    public static class ResponseErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(NetError error) {
            HttpClient.showVolleyError(error);
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
