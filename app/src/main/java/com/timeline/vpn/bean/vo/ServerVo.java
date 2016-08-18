package com.timeline.vpn.bean.vo;

import java.util.List;

/**
 * @author gqli
 * @date 2016年3月7日 下午1:37:13
 * @version V1.0
 */
public class ServerVo {
    public String name;
    public String pwd;
    public long expire;
    public int type;
    public HostVo bestHost;
    public List<HostVo> hostList;

}

