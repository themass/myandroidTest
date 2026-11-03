package com.openapi.data.vpn.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.repository.TunnelEngine;
import com.openapi.domain.repository.TunnelRequest;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;

/**
 * WireGuard 引擎，基于官方 wg-go 用户态实现（com.wireguard.android:tunnel）。
 * <p>
 * 相比 libipsec 的关键优势：
 * <ul>
 *   <li>无状态设计，没有常驻线程与定时器，待机耗电更低；</li>
 *   <li>CryptoKeyUpdate 每 2 分钟一次，滑动窗口重放保护在协议层完成；</li>
 *   <li>握手 1-RTT（Noise IK），比 IKEv2 的完整流程更短；</li>
 *   <li>用户态转发路径比 libipsec 成熟，移动端吞吐通常高 2–4 倍。</li>
 * </ul>
 * 劣势：固定头部特征明显，UDP 端口易被识别与封锁，因此定位为"快速通道"，
 * 由自动选优在可用时优先使用，不可用时回退 IKEv2。
 * <p>
 * 许可：WireGuard 官方库为 Apache-2.0，与本项目的 GPL-2.0 兼容。
 */
public final class WireGuardTunnelEngine implements TunnelEngine {

    private static final String TAG = "WireGuardEngine";
    private static final String TUNNEL_NAME = "wg0";

    private final android.content.Context appContext;
    private Backend backend;
    @Nullable
    private Callback callback;
    @Nullable
    private Tunnel tunnel;
    private volatile boolean connected;

    public WireGuardTunnelEngine(@NonNull android.content.Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public TunnelProtocol protocol() {
        return TunnelProtocol.WIREGUARD;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.wireguard.android.backend.GoBackend");
            System.loadLibrary("wg-go");
            return true;
        } catch (Throwable t) {
            LogUtil.e(TAG + ": wg-go 不可用: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void connect(@NonNull TunnelRequest request, @NonNull Callback callback) {
        this.callback = callback;
        callback.onConnecting();
        if (request.getPeerPublicKey() == null || request.getClientAddress() == null) {
            callback.onError(ErrorCode.CONFIG_INVALID,
                    "WireGuard 需要 peerPublicKey 与 clientAddress");
            return;
        }
        /*
         * WireGuard 没有账号密码，服务端必须事先认识本机公钥才会接受握手。
         * 因此真正建隧道前先确保公钥已注册；注册接口后端尚未实现时
         * （见 WireGuardRegistrationUtil 的说明），这里会收到 onFailed，
         * 并以 CONFIG_INVALID 上报——上层据此把本次尝试记为失败、
         * 自动选优会据此把 WireGuard 降权，下次优先用 IKEv2。
         */
        com.openapi.data.remote.WireGuardRegistrationUtil.ensureRegistered(appContext,
                new com.openapi.data.remote.WireGuardRegistrationUtil.Callback() {
                    @Override
                    public void onReady() {
                        establishTunnel(request, callback);
                    }

                    @Override
                    public void onFailed(@NonNull String reason) {
                        LogUtil.e(TAG + ": 公钥未注册，放弃本次连接: " + reason);
                        callback.onError(ErrorCode.CONFIG_INVALID, "WireGuard 身份未注册: " + reason);
                    }
                });
    }

    private void establishTunnel(@NonNull TunnelRequest request, @NonNull Callback callback) {
        try {
            Config config = buildConfig(request);
            GoBackend go = new GoBackend(appContext);
            backend = go;
            tunnel = new SimpleTunnel(TUNNEL_NAME, callback);
            go.setState(tunnel, Tunnel.State.UP, config);
            connected = go.getState(tunnel) == Tunnel.State.UP;
            if (connected) {
                callback.onConnected();
            } else {
                callback.onDisconnected();
            }
        } catch (BadConfigException e) {
            LogUtil.e(TAG + ": 配置非法: " + e.getMessage());
            callback.onError(ErrorCode.CONFIG_INVALID, e.getMessage());
        } catch (Exception e) {
            LogUtil.e(TAG + ": 连接失败: " + e.getMessage());
            callback.onError(ErrorCode.GENERIC, e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        Callback cb = callback;
        if (cb != null) {
            cb.onDisconnecting();
        }
        try {
            if (backend != null && tunnel != null) {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
            }
        } catch (Exception e) {
            LogUtil.e(TAG + ": 断开异常: " + e.getMessage());
        }
        connected = false;
        if (cb != null) {
            cb.onDisconnected();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void release() {
        disconnect();
        backend = null;
        tunnel = null;
        callback = null;
    }

    // --------------------------------------------------------------- private

    @NonNull
    private Config buildConfig(@NonNull TunnelRequest request) throws BadConfigException {
        Config.Builder cb = new Config.Builder();

        com.wireguard.config.Interface.Builder ib = new com.wireguard.config.Interface.Builder();
        // WireGuard 没有账号密码，身份就是本地长期密钥对；公钥需事先注册到服务端。
        com.wireguard.crypto.KeyPair identity =
                com.openapi.data.local.WireGuardKeyStore.getOrCreate(appContext);
        ib.parsePrivateKey(identity.getPrivateKey().toBase64());
        ib.parseAddresses(request.getClientAddress());
        if (request.getDnsServer() != null && !request.getDnsServer().isEmpty()) {
            ib.parseDnsServers(request.getDnsServer());
        }
        if (request.getMtu() > 0) {
            ib.parseMtu(String.valueOf(request.getMtu()));
        }
        // 分应用代理：WireGuard 与 VpnService.Builder 语义一致，
        // INCLUDE 与 EXCLUDE 互斥，只能二选一。
        com.openapi.domain.model.SplitTunnelingPolicy policy = request.getSplitTunneling();
        if (!policy.isAllThrough()) {
            if (policy.getMode() == com.openapi.domain.model.SplitTunnelingPolicy.Mode.INCLUDE) {
                for (String pkg : policy.getPackages()) {
                    ib.includeApplication(pkg);
                }
            } else {
                for (String pkg : policy.getPackages()) {
                    ib.excludeApplication(pkg);
                }
            }
        }
        cb.setInterface(ib.build());

        com.wireguard.config.Peer.Builder pb = new com.wireguard.config.Peer.Builder();
        pb.parsePublicKey(request.getPeerPublicKey());
        String endpoint = request.getServer() + ":" + request.resolveWireGuardPort();
        pb.parseEndpoint(endpoint);
        pb.parseAllowedIPs("0.0.0.0/0, ::/0");
        cb.addPeer(pb.build());

        return cb.build();
    }

    /** 极简 Tunnel 实现：GoBackend 需要它来标识隧道并回调状态。 */
    private static final class SimpleTunnel implements Tunnel {
        private final String name;
        private final Callback callback;

        SimpleTunnel(String name, Callback callback) {
            this.name = name;
            this.callback = callback;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void onStateChange(State newState) {
            // 状态已由 connect()/disconnect() 同步返回，这里只做日志与兜底通知。
            LogUtil.i(TAG + ": tunnel state -> " + newState);
            if (newState == State.DOWN) {
                callback.onDisconnected();
            }
        }
    }
}
