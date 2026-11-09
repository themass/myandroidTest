package com.openapi.ks.myapp.bean.vo;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Created by openapi on 2017/9/3.
 */
@Entity(tableName = "HistoryVo", indices = {@Index(value = "itemUrl", unique = true)})
public class HistoryVo {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String itemUrl;

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
