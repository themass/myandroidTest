package com.qq;

import com.qq.ext.util.AES2;
import com.qq.ext.util.GsonUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.StringUtils;
import com.qq.vpn.domain.res.HostVo;
import com.qq.vpn.domain.res.InfoListVo;
import com.qq.vpn.domain.res.JsonResult;
import com.qq.vpn.domain.res.VpnProfile;

import java.lang.reflect.Type;

/**
 * Created by dengt on 2016/3/10.
 */
public class BeanBuilder {
    public static VpnProfile builderVpnProfile(long expire, String name, String pwd, HostVo vo) {
        VpnProfile profile = new VpnProfile();
        profile.setExpire(expire);
        profile.setVpnType(Constants.vpnType);
        profile.setCert(vo.cert);
        profile.setGateway(vo.gateway);
        profile.setName("client");
        profile.setPort(vo.port);
        profile.setPassword(pwd);
        profile.setUsername(name);
        profile.setCert(vo.cert);
        LogUtil.i(vo.cert);
        return profile;
    }

    public static <T> JsonResult<T> parserVo(Class<T> clasz, String json, String key) {
        if (clasz == null) {
            clasz = (Class<T>) Object.class;
        }
        Type typeOfT = GsonUtils.type(JsonResult.class, clasz);
        JsonResult<T> ret = GsonUtils.getInstance().fromJson(json, typeOfT);
        if (StringUtils.hasText(ret.data)) {
            String str = AES2.decode(ret.data, key);
            ret.objData = GsonUtils.getInstance().fromJson(str, clasz);
        }
        return ret;
    }

    public static <T> JsonResult<InfoListVo<T>> parserListVo(Class<T> clasz, String json, String key) {
        if (clasz == null) {
            clasz = (Class<T>) Object.class;
        }
        Type TypeItem = GsonUtils.type(InfoListVo.class, clasz);
        Type typeOfT = GsonUtils.type(JsonResult.class, TypeItem);
        JsonResult<InfoListVo<T>> ret = GsonUtils.getInstance().fromJson(json, typeOfT);
        if (StringUtils.hasText(ret.data)) {
            String str = AES2.decode(ret.data, key);
            ret.objData = GsonUtils.getInstance().fromJson(str, TypeItem);
        }
        return ret;
    }
}
