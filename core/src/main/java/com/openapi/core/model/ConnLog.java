package com.openapi.core.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ConnLog")
public class ConnLog {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public String host;
    public String userIp;
    public int status;
    public String time;

    public ConnLog() {
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

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUserIp() {
        return this.userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTime() {
        return this.time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
