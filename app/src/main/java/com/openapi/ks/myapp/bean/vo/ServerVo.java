package com.openapi.ks.myapp.bean.vo;

import java.util.List;

/**
 * @author openapi
 * @version V1.0
 * @date 2016年3月7日 下午1:37:13
 */
public class ServerVo {
    public String name;
    public String pwd;
    public long expire;
    public int type;
    public HostVo bestHost;
    public List<HostVo> hostList;

}

