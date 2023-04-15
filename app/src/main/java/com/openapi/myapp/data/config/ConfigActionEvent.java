package com.openapi.myapp.data.config;

import android.content.Context;

import java.util.Map;

/**
 * Created by dengt on 2016/3/17.
 */
public class ConfigActionEvent {
    public String url;
    public String title;
    public Context context;
    public Map<String, Object> param;

    public ConfigActionEvent(Context context, String url, String title, Map<String, Object> param) {
        this.context = context;
        this.url = url;
        this.title = title;
        this.param = param;
    }
}
