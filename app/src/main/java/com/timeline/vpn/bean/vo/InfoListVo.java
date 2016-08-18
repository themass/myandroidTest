package com.timeline.vpn.bean.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gqli on 2016/3/13.
 */
public class InfoListVo<T> {
    public List<T> voList = new ArrayList<>();
    public int total;
    public boolean hasMore;
    @Override
    public String toString() {
        return "InfoListVo{" +
                "voList=" + voList +
                ", total=" + total +
                ", hasMore=" + hasMore +
                '}';
    }
}
