package com.openapi.ks.myapp.bean.vo;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Transient;
import org.greenrobot.greendao.annotation.Unique;

/**
 * Created by openapi on 2017/9/3.
 */
@Entity
public class FavoriteVo {
    @Id(autoincrement = true)
    public Long id;
    public String name;
    public String updateTime;
    public int type;
    @Unique
    public String itemUrl;
    public String extra;
    @Transient
    public Object o;

    @Generated(hash = 554894695)
    public FavoriteVo(Long id, String name, String updateTime, int type,
                      String itemUrl, String extra) {
        this.id = id;
        this.name = name;
        this.updateTime = updateTime;
        this.type = type;
        this.itemUrl = itemUrl;
        this.extra = extra;
    }

    @Generated(hash = 1745425126)
    public FavoriteVo() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getItemUrl() {
        return this.itemUrl;
    }

    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl;
    }

    public String getExtra() {
        return this.extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Object getO() {
        return o;
    }

    public void setO(Object o) {
        this.o = o;
    }
}
