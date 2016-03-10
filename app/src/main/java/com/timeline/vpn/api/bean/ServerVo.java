package com.timeline.vpn.api.bean;

import java.util.List;

/**
 * @author gqli
 * @date 2016年3月7日 下午1:37:13
 * @version V1.0
 */
public class ServerVo {
    private String name;
    private String pwd;
    private long expire;
    private int type;
    private HostVo bestHost;
    private List<HostVo> hostList;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPwd() {
        return pwd;
    }
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
    public long getExpire() {
        return expire;
    }
    public void setExpire(long expire) {
        this.expire = expire;
    }
    public List<HostVo> getHostList() {
        return hostList;
    }
    public void setHostList(List<HostVo> hostList) {
        this.hostList = hostList;
    }
    public int getType() {
        return type;
    }
    public void setType(int type) {
        this.type = type;
    }

    public HostVo getBestHost() {
        return bestHost;
    }

    public void setBestHost(HostVo bestHost) {
        this.bestHost = bestHost;
    }
}

