package com.openapi.common.util.lang;

import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.log.LogUtil;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by openapi on 2016/8/14.
 */
public class BeanUtil {

    public static Map<String, String> transBean2Map(Object obj) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        //key值 应该是 Person类中的属性名，利用反射机制
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                Object val = field.get(obj);//得到此属性的值
                // null 不能转成字符串：String.valueOf(null) 是 "null"，服务端会把它当成真的值
                // （拍照解题的可选字段 subject/grade 会变成「学生提示的学科：null」）。直接不发这个字段。
                if (val == null) {
                    continue;
                }
                map.put(field.getName(), String.valueOf(val));
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
        return map;
    }
}
