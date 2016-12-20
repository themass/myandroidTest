package com.timeline.vpn.common.util;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by themass on 2016/3/17.
 */
public class EventBusUtil {
    private static EventBus eventBus;

    static {
        eventBus = EventBus.builder().build();
    }

    public static EventBus getEventBus() {
        return eventBus;
    }
}
