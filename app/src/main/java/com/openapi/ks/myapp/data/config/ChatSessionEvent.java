package com.openapi.ks.myapp.data.config;

/**
 * Created by openapi on 2017/9/15.
 */


public class ChatSessionEvent {
    public Long id;
    public String settingName;

    public ChatSessionEvent(Long id, String settingName) {
        this.id = id;
        this.settingName = settingName;
    }
}
