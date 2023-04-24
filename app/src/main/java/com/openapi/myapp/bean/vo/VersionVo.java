package com.openapi.myapp.bean.vo;

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
    public Boolean update = false;
    public Integer probability=5;
    public String mobvistaNative;
    public float traf;
    public Boolean chinaUser = false;
    public Boolean checkChina = true;
    public String userIp;

    @Override
    public String toString() {
        return "VersionVo{" +
                "version='" + version + '\'' +
                ", content='" + content + '\'' +
                ", url='" + url + '\'' +
                ", minBuild=" + minBuild +
                ", maxBuild=" + maxBuild +
                ", adsShow=" + adsShow +
                ", logUp=" + logUp +
                ", stateUse=" + stateUse +
                ", vipDesc=" + vipDesc +
                ", dnspodIp='" + dnspodIp + '\'' +
                ", needDnspod=" + needDnspod +
                ", needNative=" + needNative +
                ", update=" + update +
                ", probability=" + probability +
                ", mobvistaNative='" + mobvistaNative + '\'' +
                ", traf=" + traf +
                ", chinaUser=" + chinaUser +
                ", userIp='" + userIp + '\'' +
                '}';
    }
}
