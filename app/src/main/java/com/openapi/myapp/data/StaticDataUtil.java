package com.openapi.myapp.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2016/4/6.
 */
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
            return (T) data.get(key);
        } catch (Exception e) {
            data.remove(key);
            return null;
        }
    }

    public static <T> T get(String key, Class<T> clasz, T defaultVal) {
        try {
            T ret = (T) data.get(key);
            return ret == null ? defaultVal : ret;
        } catch (Exception e) {
            data.remove(key);
            return null;
        }
    }

    public static void clear() {
        data.clear();
    }

}
