package com.openapi.commons.common.util;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by openapi on 2016/8/14.
 */
public class BeanUtil {
    public static Map convertBean(Object bean) {
        return GsonUtils.getMap(GsonUtils.getInstance().toJson(bean));
    }

    public static Map<String, String> transBean2Map(Object obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
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
