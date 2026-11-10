package com.openapi.core.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.lang.reflect.Type;

public class PreferenceUtils {
    public static String getPrefString(Context context, String key,
                                       final String defaultValue) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        return settings.getString(key, defaultValue);
    }

    public static void setPrefString(Context context, final String key,
                                     final String value) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().putString(key, value).apply();
    }

    public static void setPrefObj(Context context, final String key,
                                  final Object value) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().putString(key, GsonUtils.getInstance().toJson(value)).apply();
    }

    public static boolean getPrefBoolean(Context context, final String key,
                                         final boolean defaultValue) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        return settings.getBoolean(key, defaultValue);
    }

    public static <T> T getPrefObj(Context context, final String key, Class<T> t) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        String data = settings.getString(key, null);
        if (data == null)
            return null;
        return GsonUtils.getInstance().fromJson(data, t);
    }

    public static <T> T getPrefObj(Context context, final String key, Type type) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        String data = settings.getString(key, null);
        if (data == null)
            return null;
        return GsonUtils.getInstance().fromJson(data, type);
    }

    public static boolean hasKey(Context context, final String key) {
        return context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).contains(
                key);
    }

    public static void setPrefBoolean(Context context, final String key,
                                      final boolean value) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().putBoolean(key, value).apply();
    }

    public static void setPrefInt(Context context, final String key,
                                  final int value) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().putInt(key, value).apply();
    }

    public static int getPrefInt(Context context, final String key,
                                 final int defaultValue) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        return settings.getInt(key, defaultValue);
    }



    public static void setSettingLong(Context context, final String key,
                                      final long value) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().putLong(key, value).apply();
    }

    public static long getPrefLong(Context context, final String key,
                                   final long defaultValue) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        return settings.getLong(key, defaultValue);
    }


    public static void remove(Context context, final String key) {
        final SharedPreferences settings = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        settings.edit().remove(key).apply();
    }
}
