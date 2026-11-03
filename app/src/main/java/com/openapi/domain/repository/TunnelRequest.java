package com.openapi.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.domain.model.SplitTunnelingPolicy;

/**
 * 建立隧道所需的参数，协议无关。
 * <p>
 * 各引擎只取自己需要的字段；不适用的字段留空即可。这样上层可以用同一份
 * 连接意图驱动不同协议，而不必为每种协议维护一套参数类型。
 */
public final class TunnelRequest {

    /** 服务器地址（host 或 IP）。 */
    @NonNull
    private final String server;
    /** 端口；未指定时由各引擎用协议默认值。 */
    private final int port;
    @Nullable
    private final String username;
    @Nullable
    private final String password;
    /** 服务器下发的证书（PEM），IKEv2 使用。 */
    @Nullable
    private final String certificate;
    /** WireGuard 的服务端公钥，Base64。 */
    @Nullable
    private final String peerPublicKey;
    /** WireGuard 分配给客户端的地址（CIDR，可多个逗号分隔）。 */
    @Nullable
    private final String clientAddress;
    @Nullable
    private final String dnsServer;
    private final int mtu;
    /**
     * WireGuard 端点端口。0 表示未单独指定，回退到 {@link #getPort()}。
     * <p>
     * 必须与 IKEv2 端口分开：同一个节点若同时支持 IKEv2（500/4500）与
     * WireGuard（惯例 51820），单一 port 字段会让其中一个协议连到错误端口。
     */
    private final int wireGuardPort;
    @Nullable
    private final String reserved;
    @NonNull
    private final SplitTunnelingPolicy splitTunneling;

    // ---- VLESS+Reality 专用字段 ----
    /** VLESS 用户 UUID。 */
    @Nullable
    private final String vlessUuid;
    /** 流控，现代服务端用 "xtls-rprx-vision"。 */
    @Nullable
    private final String vlessFlow;
    /** Reality 借用的真实网站域名（握手时发送的 SNI）。 */
    @Nullable
    private final String realityServerName;
    /** Reality 服务端公钥，Base64。 */
    @Nullable
    private final String realityPublicKey;
    /** Reality short ID，服务端分配，十六进制字符串。 */
    @Nullable
    private final String realityShortId;
    /** TLS 指纹伪装，如 "chrome"；留空时引擎用默认值。 */
    @Nullable
    private final String realityFingerprint;
    /**
     * 完整的 Xray outbound JSON（由分享链接解析得到）。非空时引擎直接使用它，
     * 忽略上面的 VLESS+Reality 字段。
     */
    @Nullable
    private final String xrayOutboundJson;

    private TunnelRequest(Builder b) {
        this.server = b.server;
        this.port = b.port;
        this.username = b.username;
        this.password = b.password;
        this.certificate = b.certificate;
        this.peerPublicKey = b.peerPublicKey;
        this.clientAddress = b.clientAddress;
        this.dnsServer = b.dnsServer;
        this.mtu = b.mtu;
        this.wireGuardPort = b.wireGuardPort;
        this.reserved = b.reserved;
        this.splitTunneling = b.splitTunneling == null
                ? SplitTunnelingPolicy.ALL_THROUGH : b.splitTunneling;
        this.vlessUuid = b.vlessUuid;
        this.vlessFlow = b.vlessFlow;
        this.realityServerName = b.realityServerName;
        this.realityPublicKey = b.realityPublicKey;
        this.realityShortId = b.realityShortId;
        this.realityFingerprint = b.realityFingerprint;
        this.xrayOutboundJson = b.xrayOutboundJson;
    }

    @NonNull
    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getCertificate() {
        return certificate;
    }

    @Nullable
    public String getPeerPublicKey() {
        return peerPublicKey;
    }

    @Nullable
    public String getClientAddress() {
        return clientAddress;
    }

    @Nullable
    public String getDnsServer() {
        return dnsServer;
    }

    public int getMtu() {
        return mtu;
    }

    /**
     * WireGuard 实际使用的端口：优先 {@link #wireGuardPort}，
     * 未单独配置时回退到 {@link #getPort()}，再回退到 51820。
     */
    public int resolveWireGuardPort() {
        if (wireGuardPort > 0) {
            return wireGuardPort;
        }
        return port > 0 ? port : 51820;
    }

    public int getWireGuardPort() {
        return wireGuardPort;
    }

    @Nullable
    public String getReserved() {
        return reserved;
    }

    /** 分应用代理策略；默认全流量走代理。 */
    @NonNull
    public SplitTunnelingPolicy getSplitTunneling() {
        return splitTunneling;
    }

    @Nullable
    public String getVlessUuid() {
        return vlessUuid;
    }

    @Nullable
    public String getVlessFlow() {
        return vlessFlow;
    }

    @Nullable
    public String getRealityServerName() {
        return realityServerName;
    }

    @Nullable
    public String getRealityPublicKey() {
        return realityPublicKey;
    }

    @Nullable
    public String getRealityShortId() {
        return realityShortId;
    }

    @Nullable
    public String getRealityFingerprint() {
        return realityFingerprint;
    }

    @Nullable
    public String getXrayOutboundJson() {
        return xrayOutboundJson;
    }

    @NonNull
    public Builder newBuilder() {
        return new Builder()
                .server(server).port(port).username(username).password(password)
                .certificate(certificate).peerPublicKey(peerPublicKey)
                .clientAddress(clientAddress).dnsServer(dnsServer).mtu(mtu)
                .wireGuardPort(wireGuardPort)
                .reserved(reserved).splitTunneling(splitTunneling)
                .vlessUuid(vlessUuid).vlessFlow(vlessFlow)
                .realityServerName(realityServerName).realityPublicKey(realityPublicKey)
                .realityShortId(realityShortId).realityFingerprint(realityFingerprint)
                .xrayOutboundJson(xrayOutboundJson);
    }

    public static final class Builder {
        private String server;
        private int port;
        private String username;
        private String password;
        private String certificate;
        private String peerPublicKey;
        private String clientAddress;
        private String dnsServer;
        private int mtu;
        private int wireGuardPort;
        private String reserved;
        private SplitTunnelingPolicy splitTunneling;
        private String vlessUuid;
        private String vlessFlow;
        private String realityServerName;
        private String realityPublicKey;
        private String realityShortId;
        private String realityFingerprint;
        private String xrayOutboundJson;

        public Builder server(String v) {
            this.server = v;
            return this;
        }

        public Builder port(int v) {
            this.port = v;
            return this;
        }

        public Builder username(String v) {
            this.username = v;
            return this;
        }

        public Builder password(String v) {
            this.password = v;
            return this;
        }

        public Builder certificate(String v) {
            this.certificate = v;
            return this;
        }

        public Builder peerPublicKey(String v) {
            this.peerPublicKey = v;
            return this;
        }

        public Builder clientAddress(String v) {
            this.clientAddress = v;
            return this;
        }

        public Builder dnsServer(String v) {
            this.dnsServer = v;
            return this;
        }

        public Builder mtu(int v) {
            this.mtu = v;
            return this;
        }

        public Builder wireGuardPort(int v) {
            this.wireGuardPort = v;
            return this;
        }

        public Builder reserved(String v) {
            this.reserved = v;
            return this;
        }

        public Builder splitTunneling(SplitTunnelingPolicy v) {
            this.splitTunneling = v;
            return this;
        }

        public Builder vlessUuid(String v) {
            this.vlessUuid = v;
            return this;
        }

        public Builder vlessFlow(String v) {
            this.vlessFlow = v;
            return this;
        }

        public Builder realityServerName(String v) {
            this.realityServerName = v;
            return this;
        }

        public Builder realityPublicKey(String v) {
            this.realityPublicKey = v;
            return this;
        }

        public Builder realityShortId(String v) {
            this.realityShortId = v;
            return this;
        }

        public Builder realityFingerprint(String v) {
            this.realityFingerprint = v;
            return this;
        }

        public Builder xrayOutboundJson(String v) {
            this.xrayOutboundJson = v;
            return this;
        }

        public TunnelRequest build() {
            if (server == null || server.trim().isEmpty()) {
                throw new IllegalStateException("server 不能为空");
            }
            return new TunnelRequest(this);
        }
    }
}
