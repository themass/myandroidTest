package com.openapi.data.vpn.engine;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.repository.TunnelEngine;
import com.openapi.domain.repository.TunnelRequest;

/**
 * VLESS+Reality 与用户自己的分享链接节点共用的引擎，由 sing-box（libbox）承载。
 * <p>
 * 这里只做两件事：把 {@link TunnelRequest} 翻译成 sing-box 配置，以及驱动
 * {@link SingBoxVpnService} 的生命周期。协议握手、Reality、TLS 指纹全部在 sing-box 内核里完成。
 * <p>
 * 定位：抗封锁通道。走 TCP/443，伪装成真实网站的 TLS 1.3 握手。吞吐和延迟不如 WireGuard，
 * 自动选优默认不会挑它（见 {@code ProtocolSelector}），需要用户显式选择。
 */
public final class SingBoxTunnelEngine implements TunnelEngine {

    private static final String TAG = "SingBoxEngine";
    private static final String DEFAULT_TUN_ADDRESS = "172.19.0.1/30";
    private static final String DEFAULT_DNS = "1.1.1.1,8.8.8.8";
    private static final String DEFAULT_FINGERPRINT = "chrome";

    private final Context appContext;
    @Nullable
    private Callback callback;
    private volatile boolean connected;

    private final SingBoxVpnService.Listener serviceListener = new SingBoxVpnService.Listener() {
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
            // 疑似 MTU 黑洞：学习值已记为 1280，自动重连一次（buildConfig 会读到学习值）
            TunnelRequest req = lastRequest;
            if (reason != null && reason.startsWith(TunnelMtuPolicy.SUSPECT_MARK)
                    && !mtuRetried && req != null) {
                mtuRetried = true;
                LogUtil.i(TAG + ": 疑似 MTU 黑洞，降到 1280 自动重连一次");
                retrying = true;
                try {
                    connect(req, callback);
                } finally {
                    retrying = false;
                }
                return;
            }
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

    public SingBoxTunnelEngine(@NonNull Context context) {
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
            Class.forName("io.nekohasekai.libbox.Libbox");
            return true;
        } catch (Throwable t) {
            LogUtil.e(TAG + ": libbox 不可用: " + t.getMessage());
            return false;
        }
    }

    /** 最近一次请求与 MTU 重试标记：用户主动连接时重置，自动降级重连时保持。 */
    private volatile TunnelRequest lastRequest;
    private boolean mtuRetried;
    private boolean retrying;

    @Override
    public void connect(@NonNull TunnelRequest request, @NonNull Callback callback) {
        if (!retrying) {
            mtuRetried = false;
        }
        lastRequest = request;
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
            String configJson = buildConfig(request);
            SingBoxVpnService.setListener(serviceListener);

            Intent prepare = VpnService.prepare(appContext);
            if (prepare != null) {
                // 权限还没拿到：上层应已完成 VpnService.prepare() 流程；走到这里说明调用方跳过了它，
                // 或者系统在这次调用前又把授权收回了。都要留痕，否则表现成"点了没反应"。
                String reason = "VPN 权限未授予（或已被其它 VPN 应用占用）";
                LogUtil.e(TAG + ": " + reason);
                callback.onError(ErrorCode.CONFIG_INVALID, reason);
                return;
            }

            Intent intent = new Intent(appContext, SingBoxVpnService.class);
            intent.setAction(SingBoxVpnService.ACTION_CONNECT);
            intent.putExtra(SingBoxVpnService.EXTRA_CONFIG_JSON, configJson);
            intent.putExtra(SingBoxVpnService.EXTRA_MTU_PROTO, mtuProto(request).name());
            intent.putExtra(SingBoxVpnService.EXTRA_MTU_NODE, nodeKey(request));

            SplitTunnelingPolicy policy = request.getSplitTunneling();
            if (!policy.isAllThrough()) {
                intent.putExtra(SingBoxVpnService.EXTRA_SPLIT_MODE, policy.getMode().name());
                intent.putExtra(SingBoxVpnService.EXTRA_SPLIT_PACKAGES,
                        policy.getPackages().toArray(new String[0]));
            }
            appContext.startService(intent);
        } catch (Exception e) {
            // IllegalArgumentException 来自转换器（协议或传输不支持），给出的文案直接面向用户。
            LogUtil.e(TAG + ": 配置构建失败: " + e.getMessage());
            callback.onError(ErrorCode.CONFIG_INVALID, e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        Intent intent = new Intent(appContext, SingBoxVpnService.class);
        intent.setAction(SingBoxVpnService.ACTION_DISCONNECT);
        appContext.startService(intent);
    }

    @Override
    public void stopSilently() {
        callback = null;
        connected = false;
        if (!SingBoxVpnService.isRunning()) {
            return;
        }
        Intent intent = new Intent(appContext, SingBoxVpnService.class);
        intent.setAction(SingBoxVpnService.ACTION_DISCONNECT);
        appContext.startService(intent);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void release() {
        disconnect();
        SingBoxVpnService.setListener(null);
        callback = null;
        connected = false;
    }

    // --------------------------------------------------------------- private

    /**
     * 组装 sing-box 配置。分享链接节点直接用解析出的 Xray outbound 经转换器翻译；
     * 服务器下发的 VLESS+Reality 先拼成同样格式的 outbound，走同一条转换路径。
     */
    @NonNull
    private static String buildConfig(@NonNull TunnelRequest request) {
        JsonObject xrayOutbound = isBlank(request.getXrayOutboundJson())
                ? vlessRealityOutbound(request)
                : JsonParser.parseString(request.getXrayOutboundJson()).getAsJsonObject();
        JsonObject proxy = SingBoxConfigBuilder.convertOutbound(xrayOutbound);
        String tunAddress = request.getClientAddress() != null
                ? request.getClientAddress() : DEFAULT_TUN_ADDRESS;
        String dns = isBlank(request.getDnsServer()) ? DEFAULT_DNS : request.getDnsServer();
        TunnelMtuPolicy.Proto proto = TunnelMtuPolicy.forSingBoxOutbound(outboundType(proxy));
        int mtu = TunnelMtuPolicy.resolve(proto, request.getMtu(), nodeKey(request));
        return SingBoxConfigBuilder.buildConfig(proxy, tunAddress, mtu, dns);
    }

    /** 探针判定疑似 MTU 黑洞时服务要写学习值，需要协议类别和节点地址，随启动参数一起传过去。 */
    private static TunnelMtuPolicy.Proto mtuProto(TunnelRequest request) {
        JsonObject xrayOutbound = isBlank(request.getXrayOutboundJson())
                ? vlessRealityOutbound(request)
                : JsonParser.parseString(request.getXrayOutboundJson()).getAsJsonObject();
        return TunnelMtuPolicy.forSingBoxOutbound(
                outboundType(SingBoxConfigBuilder.convertOutbound(xrayOutbound)));
    }

    private static String outboundType(JsonObject proxy) {
        return proxy.has("type") && proxy.get("type").isJsonPrimitive()
                ? proxy.get("type").getAsString() : null;
    }

    private static String nodeKey(TunnelRequest request) {
        return request.getServer() + ":" + request.getPort();
    }

    /**
     * 服务器下发的 VLESS+Reality 节点，拼成 Xray 格式 outbound。
     * flow 必须与服务端该用户的配置严格一致：服务端没配 flow（纯 Reality、不带 XTLS Vision）时
     * 绝不能自作主张塞默认值，否则握手直接失败。只有服务端明确配了 flow 才透传。
     */
    @NonNull
    private static JsonObject vlessRealityOutbound(@NonNull TunnelRequest request) {
        JsonObject user = new JsonObject();
        user.addProperty("id", request.getVlessUuid());
        user.addProperty("encryption", "none");
        if (!isBlank(request.getVlessFlow())) {
            user.addProperty("flow", request.getVlessFlow().trim());
        }
        JsonObject vnext = new JsonObject();
        vnext.addProperty("address", request.getServer());
        vnext.addProperty("port", request.getPort() > 0 ? request.getPort() : 443);
        JsonArray users = new JsonArray();
        users.add(user);
        vnext.add("users", users);
        JsonArray vnextList = new JsonArray();
        vnextList.add(vnext);
        JsonObject settings = new JsonObject();
        settings.add("vnext", vnextList);

        JsonObject reality = new JsonObject();
        reality.addProperty("fingerprint", request.getRealityFingerprint() != null
                ? request.getRealityFingerprint() : DEFAULT_FINGERPRINT);
        reality.addProperty("serverName", request.getRealityServerName());
        reality.addProperty("publicKey", request.getRealityPublicKey());
        reality.addProperty("shortId", request.getRealityShortId() != null
                ? request.getRealityShortId() : "");
        reality.addProperty("spiderX", "");

        JsonObject stream = new JsonObject();
        stream.addProperty("network", "tcp");
        stream.addProperty("security", "reality");
        stream.add("realitySettings", reality);

        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "vless");
        outbound.add("settings", settings);
        outbound.add("streamSettings", stream);
        return outbound;
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
        if (r.contains("config") || r.contains("invalid") || r.contains("parse")
                || r.contains("不支持")) {
            return ErrorCode.CONFIG_INVALID;
        }
        return ErrorCode.GENERIC;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
