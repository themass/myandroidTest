package com.qq.vpn.domain.res;

public class VersionVo {
    public String version;
    public String content;
    public String url;
    public int minBuild;
    public int maxBuild;
    public Boolean adsShow = false;
    public Boolean logUp = false;
    public StateUseVo stateUse;
    public VipDescVo vipDesc;
    public String dnspodIp;
    public Boolean needDnspod = true;
    public Boolean needNative = true;
    public boolean update = false;
    public Boolean showGdt = false;
    public Integer probability=4;

}
