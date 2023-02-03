package com.ks.myapp.bean.form;


import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

@Entity
public class ConnLog {
    @Id(autoincrement = true)
    public Long id;
    public String name;
    public String host;
    public String userIp;
    public int status;
    public String time;
    @Generated(hash = 773071314)
    public ConnLog(Long id, String name, String host, String userIp, int status,
            String time) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.userIp = userIp;
        this.status = status;
        this.time = time;
    }
    @Generated(hash = 760723378)
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
