package com.openapi.core.util;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.SyncStateContract.Columns;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.openapi.core.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
@SuppressWarnings("unchecked")
public class ModelUtils {



    /**
     * map 转 json
     *
     * @param map
     * @return
     */
    public static JSONObject map2Json(Map<String, Object> map) {
        JSONObject json = new JSONObject();
        if (map != null) {
            Iterator<String> iterator = map.keySet().iterator();
            while (iterator.hasNext()) {
                try {
                    String key = iterator.next();

                    if (map.containsKey(key)) {

                        Object o = map.get(key);
                        if (o == null) {
                            continue;
                        }

                        if (o instanceof Map) {
                            json.put(key, map2Json((Map<String, Object>) o));
                        } else if (o instanceof List) {
                            List list = (List) o;
                            final int length = list.size();
                            JSONArray array = new JSONArray();

                            for (int i = 0; i < length; i++) {
                                array.put(map2Json((Map<String, Object>) list.get(i)));
                            }

                            json.put(key, array);
                        } else if (o instanceof String) {
                            json.put(key, o);
                        } else {
                            json.put(key, o);
                        }
                    }
                } catch (Exception e) {
                    LogUtil.e(e);
                }

            }
        }
        return json;
    }


    /**
     * json 转 map
     *
     * @param json
     * @return
     */
    public static Map<String, Object> json2Map(JSONObject json) {
        return json2Map(json, false);
    }

    /**
     * json 转 map
     *
     * @param json
     * @param all2String 如果是true,会把所有value转成String
     * @return
     */
    public static Map<String, Object> json2Map(JSONObject json, boolean all2String) {
        Map<String, Object> map = new HashMap<String, Object>();

        if (json != null) {
            Iterator<String> iterator = json.keys();

            while (iterator.hasNext()) {
                try {
                    String key = iterator.next();

                    if (!json.isNull(key)) {

                        Object o = json.get(key);
                        if (o == null) {
                            continue;
                        }
                        if (o instanceof JSONObject) {
                            map.put(key, json2Map((JSONObject) o, all2String));
                        } else if (o instanceof JSONArray) {
                            JSONArray array = (JSONArray) o;
                            final int length = array.length();
                            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
                            for (int i = 0; i < length; i++) {
                                list.add(json2Map(array.getJSONObject(i), all2String));
                            }

                            map.put(key, list);
                        } else if (o instanceof String) {
                            map.put(key, o);
                        } else {
                            map.put(key, all2String ? o.toString() : o);
                        }
                    }
                } catch (Exception e) {
                    LogUtil.e(e);
                }

            }
        }
        return map;
    }


    /**
     * 返回map中值<br/>
     *
     * @param map
     * @param key
     * @return
     */
    public static Object getValue(Map<String, Object> map, String key) {
        if (map == null || TextUtils.isEmpty(key)) {
            return null;
        }
        Object o = map.get(key);
        if (o == null) {
            o = map.get(StringUtils.upLow(key));
        }
        return o;
    }






    /**
     * 返回String
     *
     * @param map
     * @param key
     * @return
     */
    public static String getStringValue(Map<String, Object> map, String key) {
        Object o = getValue(map, key);
        if (o != null) {
            return o.toString();
        }
        return "";
    }

    /**
     * 返回String
     *
     * @param map
     * @param key
     * @return
     */
    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object o = getValue(map, key);
        if (o != null) {
            return o.toString();
        }
        return defaultValue;
    }





    public static <T> T json2Entry(String json, Class<T> t) {
        Gson gson = new Gson();
        return gson.fromJson(json, t);
    }

    public static <T> T json2Entry(String json, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(json, type);
    }

    public static String entry2Json(Object obj) {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }
}
