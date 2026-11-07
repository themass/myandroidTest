package com.openapi.ks.myapp.bean.form;


import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Comparator;
import java.util.Date;

@Entity(tableName = "ChatSessionLog", indices = {@Index(value = "name", unique = true)})
public class ChatSessionLog implements Comparator<ChatSessionLog> {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public Date createTime;
    public String setting;
    public int type = TYPE.CUSTOMER.type;

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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    public int compare(ChatSessionLog o1, ChatSessionLog o2) {
        if(o1.type == o2.type){
            long l = o2.id - o1.id;
            return (int)l;
        }
        return o1.type== TYPE.DEF.type?1:-1;
    }

    public static enum TYPE{
        DEF(0),
        CUSTOMER(1);
        public int type;
        TYPE(int type) {
            this.type = type;
        }
    }
}
