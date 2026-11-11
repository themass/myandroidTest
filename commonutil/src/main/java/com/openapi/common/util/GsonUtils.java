package com.openapi.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by wjying on 13-12-30.
 */
public class GsonUtils {
    private static Gson gson = new GsonBuilder().create();



    /** JSON 字符串转对象。 */
    public static <T> T json2Entry(String json, Class<T> t) {
        return new Gson().fromJson(json, t);
    }

    public static <T> T json2Entry(String json, Type type) {
        return new Gson().fromJson(json, type);
    }

    /** 对象转 JSON 字符串。 */
    public static String entry2Json(Object obj) {
        return new Gson().toJson(obj);
    }

    public static Gson getInstance() {
        return gson;
    }

    public static ParameterizedType type(final Class raw, final Type... args) {
        return new ParameterizedType() {
            public Type getRawType() {
                return raw;
            }

            public Type[] getActualTypeArguments() {
                return args;
            }

            public Type getOwnerType() {
                return null;
            }
        };
    }
}
