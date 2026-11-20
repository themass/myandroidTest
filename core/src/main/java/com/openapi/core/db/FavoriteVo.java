package com.openapi.core.db;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 本地收藏。字段名不要改：它们同时是 Room 的列名和上传/展示用的 JSON 字段名。
 * Created by openapi on 2017/9/3.
 */
@Entity(tableName = "FavoriteVo", indices = {@Index(value = "itemUrl", unique = true)})
public class FavoriteVo {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public String updateTime;
    public int type;
    public String itemUrl;
    public String extra;
    /** 运行时携带的原始对象，不入库。 */
    @Ignore
    public Object o;

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
