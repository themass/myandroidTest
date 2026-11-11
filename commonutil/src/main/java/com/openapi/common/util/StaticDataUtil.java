package com.openapi.common.util;

import com.openapi.common.util.LogUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by openapi on 2016/4/6.
 */
@SuppressWarnings("unchecked")
public class StaticDataUtil {
    private static Map<String, Object> data = new HashMap<>();

    public static void add(String key, Object val) {
        data.put(key, val);
    }

    public static void del(String key) {
        data.remove(key);
    }

    public static <T> T get(String key, Class<T> clasz) {
        try {
            Object value = data.get(key);
            if (value != null && clasz.isAssignableFrom(value.getClass())) {
                return clasz.cast(value);
            }
            return null;
        } catch (Exception e) {
            data.remove(key);
            return null;
        }
    }

    public static <T> T get(String key, Class<T> clasz, T defaultVal) {
        try {
            Object value = data.get(key);
            if (value != null && clasz.isAssignableFrom(value.getClass())) {
                return clasz.cast(value);
            }
            return defaultVal;
        } catch (Exception e) {
            data.remove(key);
            return defaultVal;
        }
    }

    public static void clear() {
        data.clear();
    }
}
