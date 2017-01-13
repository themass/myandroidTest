package com.timeline.vpn.data.config;

import android.content.Context;

import java.util.Map;

/**
 * Created by themass on 2016/3/17.
 */
public class ConfigActionEvent {
    public String url;
    public Context context;
    public Map<String, Object> param;

    public ConfigActionEvent(Context context, String url, Map<String, Object> param) {
        this.context = context;
        this.url = url;
        this.param = param;
    }
}
