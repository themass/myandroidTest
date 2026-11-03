package com.openapi.myapp.bean.form;


import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class ConnLog {
    @Id(autoincrement = true)
    public Long id;
    public String name;
    public String host;
    public String userIp;
    public int status;
    public String time;

    /** 连接所耗时间（毫秒），从发起连接到 CONNECTED 的耗时；0 表示未知。 */
    public long handshakeMs;
    /** 本次连接使用的协议 id，如 "ikev2" / "wireguard"。 */
    public String protocol;
    /** 是否为自动重连产生（true 表示这是退避重试那一次，而非用户首次点击）。 */
    public boolean retry;
    @Generated(hash = 140679439)
    public ConnLog(Long id, String name, String host, String userIp, int status,
            String time, long handshakeMs, String protocol, boolean retry) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.userIp = userIp;
        this.status = status;
        this.time = time;
        this.handshakeMs = handshakeMs;
        this.protocol = protocol;
        this.retry = retry;
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
    public long getHandshakeMs() {
        return this.handshakeMs;
    }
    public void setHandshakeMs(long handshakeMs) {
        this.handshakeMs = handshakeMs;
    }
    public String getProtocol() {
        return this.protocol;
    }
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    public boolean getRetry() {
        return this.retry;
    }
    public void setRetry(boolean retry) {
        this.retry = retry;
    }
}
