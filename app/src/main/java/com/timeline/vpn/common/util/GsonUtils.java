package com.timeline.vpn.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Created by wjying on 13-12-30.
 */
public class GsonUtils {
    private static Gson gson = new GsonBuilder().create();

    public static Map<String, Object> getMap(Gson gson, String json) {
        try {
            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {
            };
            return (Map<String, Object>) (gson.fromJson(json, typeToken.getType()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map<String, Object> getMap(String json) {
        return getMap(new Gson(), json);
    }

    public static Map<String, Object> getMap(Gson gson, File file) {
        try {
            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {
            };
            return (Map<String, Object>) gson.fromJson(new FileReader(file), typeToken.getType());
        } catch (Exception ignored) {

        }
        return null;
    }

    public static List<Map<String, Object>> getMapList(Gson gson, String json) {
        try {
            TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {
            };
            return (List<Map<String, Object>>) (gson.fromJson(json, typeToken.getType()));
        } catch (Exception ignored) {

        }
        return null;
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
