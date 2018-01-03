package com.timeline.myapp.bean.vo;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Unique;

/**
 * Created by themass on 2017/9/3.
 */
@Entity
public class HistoryVo {
    @Id(autoincrement = true)
    public Long id;
    @Unique
    public String itemUrl;

    @Generated(hash = 428963280)
    public HistoryVo(Long id, String itemUrl) {
        this.id = id;
        this.itemUrl = itemUrl;
    }

    @Generated(hash = 1932015612)
    public HistoryVo() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemUrl() {
        return this.itemUrl;
    }

    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl;
    }
}
