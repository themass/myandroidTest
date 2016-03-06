package com.timeline.vpn.common.net.request;

import android.content.Context;

import com.timeline.vpn.common.util.cache.CacheTask;

import java.util.List;
import java.util.Map;

/**
 * Created by wjying on 13-12-2.
 */
public class Gson4MapListCacheTask extends CacheTask<List<Map<String, Object>>> {


    public Gson4MapListCacheTask(Context context, String url, CacheTaskResponse cacheTaskResponse) {
        super(context, url, cacheTaskResponse);
    }

    protected List<Map<String, Object>> parse(String cache) {
        try {
            List<Map<String, Object>> list = GsonUtils.getMapList(getGson(), cache);
            dealResultMapList(list);
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    protected void dealResultMapList(List<Map<String, Object>> list) {

    }
}
