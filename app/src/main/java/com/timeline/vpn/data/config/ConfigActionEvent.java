package com.timeline.vpn.data.config;

import android.content.Context;

/**
 * Created by themass on 2016/3/17.
 */
public class ConfigActionEvent {
    public String url;
    public Context context;

    public ConfigActionEvent(Context context, String url) {
        this.context = context;
        this.url = url;
    }
}
