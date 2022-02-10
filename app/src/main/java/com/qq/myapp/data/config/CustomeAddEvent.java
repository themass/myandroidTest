package com.qq.myapp.data.config;

import java.util.Map;

/**
 * Created by dengt on 2016/3/17.
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