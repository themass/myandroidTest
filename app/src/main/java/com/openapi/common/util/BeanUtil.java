package com.openapi.common.util;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by dengt on 2016/8/14.
 */
public class BeanUtil {
    public static Map convertBean(Object bean) {
        return GsonUtils.getMap(GsonUtils.getInstance().toJson(bean));
    }

    public static Map<String, String> transBean2Map(Object obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        // 调用方直接传 Map 时原样用。反射 HashMap 只会拿到它的内部字段（table、modCount），
        // 请求体变成空的——WireGuard 公钥注册就是这样一直失败的。
        if (obj instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    map.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            return map;
        }
        //key值 应该是 Person类中的属性名，利用反射机制
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                Object val = field.get(obj);//得到此属性的值
                map.put(field.getName(), String.valueOf(val));
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
        return map;
    }
}
