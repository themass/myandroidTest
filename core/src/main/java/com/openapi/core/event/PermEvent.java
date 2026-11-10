package com.openapi.core.event;

import java.util.List;

/**
 * Created by openapi on 2017/9/15.
 */


public class PermEvent {
    public List<String> perms;
    public PermEvent(List<String> perms) {
        this.perms = perms;
    }
}
