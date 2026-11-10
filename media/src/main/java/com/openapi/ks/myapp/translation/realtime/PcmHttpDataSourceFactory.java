package com.openapi.ks.myapp.translation.realtime;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;


import java.util.HashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
final class PcmHttpDataSourceFactory {
    private static final String USER_AGENT = "User-Agent";
    private static final String REFERER = "Referer";
    // 原来在 CommonConstants 里，值不变
    private static final String USER_AGENT_DEF = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36";


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
        if (!merged.containsKey(USER_AGENT)) {
            merged.put(USER_AGENT, USER_AGENT_DEF);
        }
        if (!merged.containsKey(REFERER) && !merged.isEmpty()) {
            // keep as-is when caller already set referer via getVideoSourceHeader
        }
        factory.setDefaultRequestProperties(merged);
        return factory;
    }
}
