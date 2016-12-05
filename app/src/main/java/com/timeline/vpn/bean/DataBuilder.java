package com.timeline.vpn.bean;

import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.JsonResult;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.util.GsonUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.lang.reflect.Type;

/**
 * Created by gqli on 2016/3/10.
 */
public class DataBuilder {
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

    public static <T> JsonResult<T> parserVo(Class<T> clasz, String json) {
        Type typeOfT = GsonUtils.type(JsonResult.class, clasz);
        return GsonUtils.getInstance().fromJson(json, typeOfT);
    }

    public static <T> JsonResult<InfoListVo<T>> parserListVo(Class<T> clasz, String json) {
        Type TypeItem = GsonUtils.type(InfoListVo.class, clasz);
        Type typeOfT = GsonUtils.type(JsonResult.class, TypeItem);
        return GsonUtils.getInstance().fromJson(json, typeOfT);
    }
}
