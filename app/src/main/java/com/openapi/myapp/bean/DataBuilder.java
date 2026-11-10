package com.openapi.myapp.bean;

import com.openapi.common.util.AES2;
import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.StringUtils;
import com.openapi.myapp.bean.vo.HostVo;
import com.openapi.myapp.bean.vo.InfoListVo;
import com.openapi.myapp.bean.vo.JsonResult;
import com.openapi.myapp.bean.vo.VpnProfile;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.StaticDataUtil;

import java.lang.reflect.Type;

/**
 * Created by dengt on 2016/3/10.
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
        // 隧道 MTU 必须写死 1280：不写时 strongSwan 用 1400，ESP 封装后隧道里 1400 的包
        // 多数发不出去，表现为"能连上、浏览器小页面能开、App 自己拉节点列表卡死"。
        // 这条是 IKEv2 回退路径（connectWithBestProtocol → connect(VpnProfile)）用的，必须在这里设，
        // 只改 Ikev2TunnelEngine.toProfile() 覆盖不到。
        profile.setMTU(1280);
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
        if(StringUtils.hasText(ret.userIp))
            StaticDataUtil.add(Constants.USERIP,ret.userIp);
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
        if(StringUtils.hasText(ret.userIp))
            StaticDataUtil.add(Constants.USERIP,ret.userIp);
        return ret;
    }
}
