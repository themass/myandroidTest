package com.openapi.ks.myapp.bean.form;


import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

import java.util.Date;

@Entity
public class ChatLog {
    @Id(autoincrement = true)
    public Long id;
    public String content;
    public int who;
    public String chatId;
    public Date createTime;



    @Generated(hash = 1087848595)
    public ChatLog(Long id, String content, int who, String chatId,
            Date createTime) {
        this.id = id;
        this.content = content;
        this.who = who;
        this.chatId = chatId;
        this.createTime = createTime;
    }

    @Generated(hash = 1994978153)
    public ChatLog() {
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getWho() {
        return who;
    }

    public void setWho(int who) {
        this.who = who;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
