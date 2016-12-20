package com.timeline.vpn.bean.vo;

/**
 * @author themass
 * @version V1.0
 * @date 2016年3月7日 下午1:24:42
 */
public class HostVo {
    public String gateway;
    public int port;
    public int location;
    public int ttlTime;
    public String cert;

    @Override
    public String toString() {
        return "HostVo{" +
                "gateway='" + gateway + '\'' +
                ", port=" + port +
                ", location=" + location +
                ", ttlTime=" + ttlTime +
                '}';
    }
}

