package com.timeline.vpn.data.config;

/**
 * Created by themass on 2016/12/28.
 */
public class LogAddEvent {
    public Object data;
    public Object msg;

    public LogAddEvent(Object content) {
        this.data = content;
    }

    public LogAddEvent(String msg, Object content) {
        this.data = content;
        this.msg = msg;
    }
}
