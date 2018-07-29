package com.timeline.myapp.bean.vo;

import java.io.Serializable;
import java.util.List;

/**
 * @author gqli
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

