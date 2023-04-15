package com.openapi.ks.myapp.bean.vo;

/**
 * Created by openapi on 2016/8/17.
 */
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
    public String vitamioExt;
}
