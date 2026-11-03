package com.openapi.data.vpn.engine;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.repository.TunnelEngine;
import com.openapi.domain.repository.TunnelRequest;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * VLESS+Reality 引擎，承载在官方 Xray-core 的 gomobile 封装
 * <a href="https://github.com/XTLS/libXray">XTLS/libXray</a> 之上（MIT 许可）。
 * <p>
 * 这里只做两件事：把 {@link TunnelRequest} 翻译成 Xray 的 JSON 配置，以及驱动
 * {@link XrayVpnService} 的生命周期。VLESS 协议解析、Reality 握手、TLS 指纹伪装
 * 全部在 Xray-core 原生代码里完成——这正是选它的原因：这类协议不该自己实现。
 * <p>
 * 定位：抗封锁通道。走 TCP/443，伪装成真实网站的 TLS 1.3 握手，审查方主动探测
 * 也查不出破绽。吞吐和延迟不如 WireGuard，自动选优默认不会挑它
 * （见 {@code ProtocolSelector}），需要用户在设置里显式选择。
 */
public final class VlessRealityTunnelEngine implements TunnelEngine {

    private static final String TAG = "RealityEngine";
    private static final String DEFAULT_TUN_ADDRESS = "172.19.0.1/30";
    private static final String DEFAULT_FINGERPRINT = "chrome";

    private final Context appContext;
    @Nullable
    private Callback callback;
    private volatile boolean connected;

    private final XrayVpnService.Listener serviceListener = new XrayVpnService.Listener() {
        @Override
        public void onEstablished() {
            connected = true;
            Callback cb = callback;
            if (cb != null) {
                cb.onConnected();
            }
        }

        @Override
        public void onFailed(String reason) {
            connected = false;
            Callback cb = callback;
            if (cb != null) {
                cb.onError(classifyError(reason), reason);
            }
        }

        @Override
        public void onStopped() {
            connected = false;
            Callback cb = callback;
            if (cb != null) {
                cb.onDisconnected();
            }
        }
    };

    public VlessRealityTunnelEngine(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public TunnelProtocol protocol() {
        return TunnelProtocol.VLESS_REALITY;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("libXray.LibXray");
            return true;
        } catch (Throwable t) {
            LogUtil.e(TAG + ": libXray 不可用: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void connect(@NonNull TunnelRequest request, @NonNull Callback callback) {
        this.callback = callback;
        callback.onConnecting();
        LogUtil.i(TAG + ": connect() 被调用，server=" + request.getServer()
                + ":" + request.getPort()
                + " uuid=" + (isBlank(request.getVlessUuid()) ? "缺失" : "已设置")
                + " pbk=" + (isBlank(request.getRealityPublicKey()) ? "缺失" : "已设置")
                + " sni=" + request.getRealityServerName());

        boolean fromShareLink = !isBlank(request.getXrayOutboundJson());
        if (!fromShareLink && (isBlank(request.getVlessUuid()) || isBlank(request.getRealityPublicKey())
                || isBlank(request.getRealityServerName()))) {
            String reason = "VLESS+Reality 需要 vlessUuid / realityPublicKey / realityServerName";
            LogUtil.e(TAG + ": " + reason);
            callback.onError(ErrorCode.CONFIG_INVALID, reason);
            return;
        }

        try {
            String xrayConfigJson = buildXrayConfig(request);
            XrayVpnService.setListener(serviceListener);

            Intent prepare = VpnService.prepare(appContext);
            if (prepare != null) {
                // 权限还没拿到：上层（Fragment）在发起 connect() 前应已完成
                // VpnService.prepare() 流程；走到这里说明调用方跳过了这一步，
                // 或者系统在这次调用前又把授权收回了（例如另一个 App 抢占了 VPN
                // 插槽）。无论哪种，都要留痕，否则表现成"点了没反应"，无从排查。
                String reason = "VPN 权限未授予（或已被其它 VPN 应用占用）";
                LogUtil.e(TAG + ": " + reason);
                callback.onError(ErrorCode.CONFIG_INVALID, reason);
                return;
            }

            Intent intent = new Intent(appContext, XrayVpnService.class);
            intent.setAction(XrayVpnService.ACTION_CONNECT);
            intent.putExtra(XrayVpnService.EXTRA_XRAY_CONFIG_JSON, xrayConfigJson);
            intent.putExtra(XrayVpnService.EXTRA_TUN_ADDRESS,
                    request.getClientAddress() != null ? request.getClientAddress() : DEFAULT_TUN_ADDRESS);
            intent.putExtra(XrayVpnService.EXTRA_DNS, request.getDnsServer());
            intent.putExtra(XrayVpnService.EXTRA_MTU, request.getMtu() > 0 ? request.getMtu() : 1500);

            SplitTunnelingPolicy policy = request.getSplitTunneling();
            if (!policy.isAllThrough()) {
                intent.putExtra(XrayVpnService.EXTRA_SPLIT_MODE, policy.getMode().name());
                intent.putExtra(XrayVpnService.EXTRA_SPLIT_PACKAGES,
                        policy.getPackages().toArray(new String[0]));
            }
            appContext.startService(intent);
        } catch (Exception e) {
            LogUtil.e(TAG + ": 配置构建失败: " + e.getMessage());
            callback.onError(ErrorCode.CONFIG_INVALID, e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        Intent intent = new Intent(appContext, XrayVpnService.class);
        intent.setAction(XrayVpnService.ACTION_DISCONNECT);
        appContext.startService(intent);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void release() {
        disconnect();
        XrayVpnService.setListener(null);
        callback = null;
        connected = false;
    }

    // --------------------------------------------------------------- private

    /**
     * 组装 Xray 配置：tun inbound（fd 由 {@link XrayVpnService} 通过环境变量传入，
     * 这里只需要地址/MTU 与 Builder 保持一致）+ vless/reality outbound。
     */
    @NonNull
    private String buildXrayConfig(@NonNull TunnelRequest request) throws Exception {
        String tunAddress = request.getClientAddress() != null
                ? request.getClientAddress() : DEFAULT_TUN_ADDRESS;

        JSONObject tunSettings = new JSONObject()
                .put("name", "xray-tun0")
                .put("mtu", request.getMtu() > 0 ? request.getMtu() : 1500)
                .put("gateway", new JSONArray().put(tunAddress));

        JSONObject tunInbound = new JSONObject()
                .put("protocol", "tun")
                .put("settings", tunSettings);

        if (!isBlank(request.getXrayOutboundJson())) {
            // 分享链接已解析为完整 outbound（vless/vmess/ss/trojan/hy2 通用），直接使用。
            JSONObject outbound = new JSONObject(request.getXrayOutboundJson());
            return new JSONObject()
                    .put("inbounds", new JSONArray().put(tunInbound))
                    .put("outbounds", new JSONArray().put(outbound))
                    .toString();
        }

        JSONObject user = new JSONObject()
                .put("id", request.getVlessUuid())
                .put("encryption", "none");
        // flow 必须跟服务端该用户的配置严格一致：服务端没配 flow（常见于纯
        // Reality、不带 XTLS Vision 的场景）时，这里绝不能自作主张塞一个默认值
        // ——Xray 要求客户端的 flow 与服务端配置完全匹配，塞错值直接握手失败。
        // 只有服务端明确配了 flow（分享链接里带 &flow=...）才透传。
        if (request.getVlessFlow() != null && !request.getVlessFlow().trim().isEmpty()) {
            user.put("flow", request.getVlessFlow());
        }

        JSONObject vnext = new JSONObject()
                .put("address", request.getServer())
                .put("port", request.getPort() > 0 ? request.getPort() : 443)
                .put("users", new JSONArray().put(user));

        JSONObject outboundSettings = new JSONObject()
                .put("vnext", new JSONArray().put(vnext));

        JSONObject realitySettings = new JSONObject()
                .put("fingerprint", request.getRealityFingerprint() != null
                        ? request.getRealityFingerprint() : DEFAULT_FINGERPRINT)
                .put("serverName", request.getRealityServerName())
                .put("publicKey", request.getRealityPublicKey())
                .put("shortId", request.getRealityShortId() != null
                        ? request.getRealityShortId() : "")
                .put("spiderX", "");

        JSONObject streamSettings = new JSONObject()
                .put("network", "tcp")
                .put("security", "reality")
                .put("realitySettings", realitySettings);

        JSONObject vlessOutbound = new JSONObject()
                .put("protocol", "vless")
                .put("settings", outboundSettings)
                .put("streamSettings", streamSettings);

        return new JSONObject()
                .put("inbounds", new JSONArray().put(tunInbound))
                .put("outbounds", new JSONArray().put(vlessOutbound))
                .toString();
    }

    @NonNull
    private static ErrorCode classifyError(@Nullable String reason) {
        if (reason == null) {
            return ErrorCode.GENERIC;
        }
        String r = reason.toLowerCase(java.util.Locale.ROOT);
        if (r.contains("timeout") || r.contains("unreachable") || r.contains("no route")) {
            return ErrorCode.UNREACHABLE;
        }
        if (r.contains("tls") || r.contains("reality") || r.contains("certificate")) {
            return ErrorCode.PEER_AUTH_FAILED;
        }
        if (r.contains("dns") || r.contains("resolve") || r.contains("lookup")) {
            return ErrorCode.LOOKUP_FAILED;
        }
        if (r.contains("config") || r.contains("invalid") || r.contains("parse")) {
            return ErrorCode.CONFIG_INVALID;
        }
        return ErrorCode.GENERIC;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
