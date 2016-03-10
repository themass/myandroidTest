package com.timeline.vpn.api.bean;

import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/10.
 */
public class DataBuilder {
    public static VpnProfile builderVpnProfile(long expire,String name,String pwd,HostVo vo){
        VpnProfile profile = new VpnProfile();
        profile.setExpire(expire);
        profile.setVpnType(Constants.vpnType);
        profile.setCert(vo.getCert());
        profile.setGateway(vo.getGateway());
        profile.setName(vo.getGateway());
        profile.setPort(vo.getPort());
        profile.setPassword(pwd);
        profile.setName(name);
        return profile;
    }
}
