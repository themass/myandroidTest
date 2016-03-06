package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.timeline.vpn.common.util.cache.CacheTask;

import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class Gson4MapCacheTask extends CacheTask<Map<String, Object>> {


    public Gson4MapCacheTask(Context context, String url, CacheTaskResponse cacheTaskResponse) {
        super(context, url, cacheTaskResponse);
    }

    protected Map<String, Object> parse(String cache) {
        try {
            Map<String, Object> map = GsonUtils.getMap(getGson(), cache);
            dealResultMap(map);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    protected void dealResultMap(Map<String, Object> map) {

    }
}
