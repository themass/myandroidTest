package com.openapi.ks.myapp.translation.realtime;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;

import com.openapi.core.CommonConstants;

import java.util.HashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
final class PcmHttpDataSourceFactory {

    private PcmHttpDataSourceFactory() {
    }

    static DefaultHttpDataSource.Factory create(Map<String, String> headers) {
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
        factory.setAllowCrossProtocolRedirects(true);
        factory.setConnectTimeoutMs(20_000);
        factory.setReadTimeoutMs(20_000);
        HashMap<String, String> merged = new HashMap<>();
        if (headers != null) {
            merged.putAll(headers);
        }
        if (!merged.containsKey(CommonConstants.USER_AGENT)) {
            merged.put(CommonConstants.USER_AGENT, CommonConstants.USER_AGENT_DEF);
        }
        if (!merged.containsKey(CommonConstants.REFERER) && !merged.isEmpty()) {
            // keep as-is when caller already set referer via getVideoSourceHeader
        }
        factory.setDefaultRequestProperties(merged);
        return factory;
    }
}
