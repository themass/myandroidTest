package com.openapi.core.model;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Comparator;
import java.util.Date;

/**
 * 会话列表的本地缓存。会话 id 是服务端分配的（与 vpn.chat_session.id 一致），本地不再自增。
 * 名字不再唯一：服务端允许重名，唯一索引会让同步时互相覆盖（见 AppDatabase.MIGRATION_2_3）。
 */
@Entity(tableName = "ChatSessionLog")
public class ChatSessionLog implements Comparator<ChatSessionLog> {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public Date createTime;
    /** 服务端 updatedAt（毫秒）：列表按它倒序，与服务端一致。 */
    public long updatedAt;
    public String setting;
    public int type = TYPE.CUSTOMER.type;

    public ChatSessionLog() {
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
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
