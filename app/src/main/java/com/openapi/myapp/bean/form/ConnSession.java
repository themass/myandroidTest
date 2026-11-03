package com.openapi.myapp.bean.form;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

/**
 * 一次 VPN 连接会话。
 * <p>
 * 与 {@link ConnLog} 不同：ConnLog 上传后会被清空，而会话表只保留本地统计所需的数据，
 * 由 {@code SessionRecorder} 在连接开始时插入、采样时更新、断开时收尾。
 * endAt 为 0 表示会话仍在进行中（或进程被杀后尚未收尾）。
 */
@Entity
public class ConnSession {

    @Id(autoincrement = true)
    public Long id;

    /** 开始时间（毫秒）。 */
    public long startAt;

    /** 结束时间（毫秒）；0 表示未结束。 */
    public long endAt;

    /** 节点标识：连接时的节点地址（HostVo.gateway）。 */
    public String node;

    /** 协议名，例如 IKEV2 / WIREGUARD / VLESS_REALITY。 */
    public String protocol;

    /** 本次会话期间经过隧道的下行字节数。 */
    public long rxBytes;

    /** 本次会话期间经过隧道的上行字节数。 */
    public long txBytes;

    /** 会话是否仍在进行中。进程被杀后残留的 open 行会在下次启动时被收尾。 */
    public boolean open;

    @Generated(hash = 1301984177)
    public ConnSession() {
    }

    @Generated(hash = 734345311)
    public ConnSession(Long id, long startAt, long endAt, String node, String protocol,
            long rxBytes, long txBytes, boolean open) {
        this.id = id;
        this.startAt = startAt;
        this.endAt = endAt;
        this.node = node;
        this.protocol = protocol;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
        this.open = open;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getStartAt() {
        return this.startAt;
    }

    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }

    public long getEndAt() {
        return this.endAt;
    }

    public void setEndAt(long endAt) {
        this.endAt = endAt;
    }

    public String getNode() {
        return this.node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public long getRxBytes() {
        return this.rxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public long getTxBytes() {
        return this.txBytes;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }

    public boolean getOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
