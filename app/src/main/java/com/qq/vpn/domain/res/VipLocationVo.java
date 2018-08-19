package com.qq.vpn.domain.res;

import java.io.Serializable;
import java.util.List;

/**
 * Created by dengt on 14-03-12.
 * @date 2016年8月9日 下午12:20:59
 * @version V1.0
 */
public class VipLocationVo implements Serializable{
    public int type;
    public String name;
    public int count;
    public String desc;
    public String ename;
    public String edesc;
    public List<LocationVo> list;
}

