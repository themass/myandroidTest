package com.openapi.ks.myapp.bean.form;


import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

import chat.ui.data.model.MessageType;

@Entity(
        tableName = "ChatLog",
        indices = {
                @Index(value = "chatId", unique = true),
                @Index(value = "sessionId")
        }
)
public class ChatLog {


    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String content;
    public int who;
    public int msgType = MessageType.TXT_TYPE;
    public String chatId;
    public Date createTime;

    public Long sessionId;

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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }
}
