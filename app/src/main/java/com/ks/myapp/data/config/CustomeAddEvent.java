package com.ks.myapp.data.config;

import java.util.Map;

/**
 * Created by themass on 2016/3/17.
 */
public class CustomeAddEvent {
    public String url;
    public String title;
    public Integer id;
    public Map<String, Object> param;

    public CustomeAddEvent(Integer id, String url, String title, Map<String, Object> param) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.param = param;
    }
}
