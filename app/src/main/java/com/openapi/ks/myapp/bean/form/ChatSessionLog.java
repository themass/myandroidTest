package com.openapi.ks.myapp.bean.form;


import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;

import java.util.Date;

@Entity
public class ChatSessionLog {
    @Id(autoincrement = true)
    public Long id;
    public String name;
    public Date createTime;
    public String setting;

    @Generated(hash = 200160533)
    public ChatSessionLog(Long id, String name, Date createTime, String setting) {
        this.id = id;
        this.name = name;
        this.createTime = createTime;
        this.setting = setting;
    }

    @Generated(hash = 510450089)
    public ChatSessionLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getSetting() {
        return setting;
    }

    public void setSetting(String setting) {
        this.setting = setting;
    }
}
