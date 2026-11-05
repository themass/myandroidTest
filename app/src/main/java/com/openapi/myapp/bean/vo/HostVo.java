package com.openapi.myapp.bean.vo;

/**
 * @author dengt
 * @version V1.0
 * @date 2016年3月7日 下午1:24:42
 */
public class HostVo {
    public String gateway;
    public int port;
    public int location;
    public int ttlTime;
    public String cert;

    // ---- 多协议扩展字段（服务端可不下发，缺失时自动回退 IKEv2） ----

    /** 该节点支持的协议 id，逗号分隔，如 "ikev2,wireguard"；为空表示仅 IKEv2。 */
    public String protocols;

    /** WireGuard：服务端公钥（Base64）。 */
    public String peerPublicKey;

    /** WireGuard：已弃用，客户端不再使用。每台设备的地址由注册接口返回。 */
    public String clientAddress;

    /** WireGuard：服务端 DNS。 */
    public String dns;

    /** WireGuard：预留字段（部分服务商的客户端标识）。 */
    public String reserved;

    /** VLESS：用户 UUID。 */
    public String vlessUuid;

    /** VLESS：流控，现代服务端用 "xtls-rprx-vision"。 */
    public String vlessFlow;

    /** Reality：借用的真实网站域名（握手 SNI）。 */
    public String realityServerName;

    /** Reality：服务端公钥，Base64。 */
    public String realityPublicKey;

    /** Reality：short ID，服务端分配。 */
    public String realityShortId;

    /** Reality：TLS 指纹伪装，如 "chrome"。 */
    public String realityFingerprint;

    /**
     * 通用代理节点（sing-box 承载）：协议名 -> 分享链接 的 JSON，
     * 如 {"trojan":"trojan://...","hysteria2":"hysteria2://..."}。
     * 解析与连接路径和用户自己的节点相同，见 {@link com.openapi.data.remote.ProxyLinkSelector}。
     */
    public String proxyLinks;

    /** 建议 MTU；<=0 表示由协议默认值决定。 */
    public int mtu;

    /**
     * WireGuard 端点端口；0 表示未单独下发，客户端回退到 {@link #port}。
     * <p>
     * 单独一个字段是因为同一节点常常 IKEv2 走 500/4500 而 WireGuard 走 51820，
     * 共用一个 {@link #port} 会导致其中一个协议连错端口。
     */
    public int wireGuardPort;

    /** 该节点是否声明支持指定协议。 */
    public boolean supports(String protocolId) {
        if (protocols == null || protocols.trim().isEmpty()) {
            // 未声明时保持旧行为：只支持 IKEv2。
            return "ikev2".equals(protocolId);
        }
        for (String p : protocols.split(",")) {
            if (p.trim().equalsIgnoreCase(protocolId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * WireGuard 参数是否齐全，可作为该协议的候选。
     * 只看服务端公钥：隧道内地址是每台设备注册时由服务端分配的，不再取节点上的 clientAddress。
     */
    public boolean isWireGuardReady() {
        return supports("wireguard")
                && peerPublicKey != null && !peerPublicKey.trim().isEmpty();
    }

    /** VLESS+Reality 参数是否齐全，可作为该协议的候选。 */
    public boolean isVlessRealityReady() {
        return supports("vless-reality")
                && vlessUuid != null && !vlessUuid.trim().isEmpty()
                && realityPublicKey != null && !realityPublicKey.trim().isEmpty()
                && realityServerName != null && !realityServerName.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "HostVo{" +
                "gateway='" + gateway + '\'' +
                ", port=" + port +
                ", location=" + location +
                ", ttlTime=" + ttlTime +
                ", protocols='" + protocols + '\'' +
                '}';
    }
}
