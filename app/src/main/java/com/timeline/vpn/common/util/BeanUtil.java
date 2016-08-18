package com.timeline.vpn.common.util;

import java.util.Map;

/**
 * Created by gqli on 2016/8/14.
 */
public class BeanUtil {
    public static Map convertBean(Object bean) {
        return GsonUtils.getMap(GsonUtils.getInstance().toJson(bean));
    }
}
