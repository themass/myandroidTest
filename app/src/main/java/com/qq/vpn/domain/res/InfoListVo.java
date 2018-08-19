package com.qq.vpn.domain.res;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dengt on 2016/3/13.
 */
public class InfoListVo<T> {
    public List<T> voList = new ArrayList<>();
    public int total;
    public boolean hasMore;
    public int pageNum;

    public void copy(InfoListVo data) {
        this.hasMore = data.hasMore;
        this.pageNum = data.pageNum;
        this.total = data.total;
    }

    @Override
    public String toString() {
        return "InfoListVo{" +
                "voList=" + voList +
                ", total=" + total +
                ", hasMore=" + hasMore +
                '}';
    }
}
