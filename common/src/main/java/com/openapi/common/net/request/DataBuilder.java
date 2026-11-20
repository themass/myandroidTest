package com.openapi.common.net.request;

import com.openapi.common.util.lang.AES2;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.lang.StringUtils;

import java.lang.reflect.Type;

/**
 * Created by openapi on 2016/3/10.
 */
@SuppressWarnings("unchecked")
public class DataBuilder {

    public static <T> ApiResult<T> parserVo(Class<T> clasz, String json, String key) {
        if (clasz == null) {
            clasz = (Class<T>) Object.class;
        }
        Type typeOfT = GsonUtils.type(ApiResult.class, clasz);
        ApiResult<T> ret = GsonUtils.getInstance().fromJson(json, typeOfT);
        if (StringUtils.hasText(ret.data)) {
            String str = AES2.decode(ret.data, key);
            ret.objData = GsonUtils.getInstance().fromJson(str, clasz);
        }
        return ret;
    }

    public static <T> ApiResult<InfoListVo<T>> parserListVo(Class<T> clasz, String json, String key) {
        if (clasz == null) {
            clasz = (Class<T>) Object.class;
        }
        Type TypeItem = GsonUtils.type(InfoListVo.class, clasz);
        Type typeOfT = GsonUtils.type(ApiResult.class, TypeItem);
        ApiResult<InfoListVo<T>> ret = GsonUtils.getInstance().fromJson(json, typeOfT);
        if (StringUtils.hasText(ret.data)) {
            String str = AES2.decode(ret.data, key);
            ret.objData = GsonUtils.getInstance().fromJson(str, TypeItem);
        }
        return ret;
    }
}
