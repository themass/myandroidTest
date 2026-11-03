package com.openapi.domain.model;

/**
 * 支持的隧道协议。
 * <p>
 * 每个取值对应一个 {@code TunnelEngine} 实现。新增协议时在这里加一项，
 * 其余（UI 列表、自动选优、连接流程）由引擎注册表驱动，不需要改调用方。
 */
public enum TunnelProtocol {

    /**
     * IKEv2 / IPsec，strongSwan + libipsec 用户态实现。
     * 兼容性最好，适合作为回退通道；握手 1-RTT，有状态，移动端耗电中等。
     */
    IKEV2("ikev2", "IKEv2/IPsec", true),

    /**
     * WireGuard，wg-go 用户态实现。
     * 吞吐与耗电优于 libipsec；无状态、2 分钟一次握手；头部特征明显，易被 UDP 封锁。
     */
    WIREGUARD("wireguard", "WireGuard", true),

    /**
     * VLESS + Reality，XTLS/libXray（Xray-core 官方 gomobile 封装）承载。
     * 走 TCP/443，伪装成真实网站的 TLS 1.3 握手，主动探测也查不出破绽，
     * 是目前抗封锁能力最强的方案；吞吐与延迟不如 WireGuard。
     */
    VLESS_REALITY("vless-reality", "VLESS+Reality", true),

    /**
     * 由 sing-box 内核承载的代理协议族（Hysteria2、TUIC 等）。
     * 需要额外引入 ~30MB 内核，暂未实现。
     */
    SING_BOX("sing-box", "Sing-Box", false);

    private final String id;
    private final String displayName;
    private final boolean implemented;

    TunnelProtocol(String id, String displayName, boolean implemented) {
        this.id = id;
        this.displayName = displayName;
        this.implemented = implemented;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 是否已有可用引擎；未实现的只作为路线图占位，不应出现在用户可选列表里。 */
    public boolean isImplemented() {
        return implemented;
    }

    public static TunnelProtocol fromId(String id) {
        if (id != null) {
            for (TunnelProtocol p : values()) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return IKEV2;
    }
}
