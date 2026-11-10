package com.openapi.data.vpn.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.repository.TunnelEngine;
import com.openapi.domain.repository.TunnelRequest;
import com.openapi.myapp.bean.vo.VpnProfile;
import com.openapi.myapp.constant.Constants;

import org.strongswan.android.logic.VpnStateService;

import java.util.ArrayList;
import java.util.List;

/**
 * IKEv2 / IPsec 引擎，承载在 strongSwan + libipsec 之上。
 * <p>
 * 这是项目原有的、也是目前唯一完整可用的协议实现。它把 {@link VpnStateService}
 * 的状态机翻译成协议无关的 {@link TunnelEngine.Callback} 回调，使上层不必再
 * 直接接触 strongSwan 的类型。
 */
public final class Ikev2TunnelEngine implements TunnelEngine {

    private static final String TAG = "Ikev2Engine";
    /** 隧道 MTU。见 toProfile() 里的说明。 */
    private static final int DEFAULT_MTU = 1280;

    /** 绑定状态服务的适配器由仓库注入，避免引擎自己管理 Service 生命周期。 */
    public interface StateBinder {
        /** 订阅状态变化；返回取消订阅的句柄。 */
        @NonNull
        Subscription observe(@NonNull StateObserver observer);

        /** 下发连接配置。 */
        void connect(@NonNull VpnProfile profile);

        /** 下发断开指令。 */
        void disconnect();
    }

    public interface Subscription {
        void cancel();
    }

    public interface StateObserver {
        void onState(@NonNull VpnStateService.State state,
                     @NonNull VpnStateService.ErrorState error,
                     @NonNull org.strongswan.android.logic.imc.ImcState imc);
    }

    private final StateBinder binder;
    private final List<Callback> callbacks = new ArrayList<>();
    @Nullable
    private Subscription subscription;
    @Nullable
    private Callback activeCallback;
    private volatile boolean connected;

    public Ikev2TunnelEngine(@NonNull StateBinder binder) {
        this.binder = binder;
    }

    @NonNull
    @Override
    public TunnelProtocol protocol() {
        return TunnelProtocol.IKEV2;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.strongswan.android.logic.CharonVpnService");
            return true;
        } catch (ClassNotFoundException e) {
            LogUtil.e(TAG + ": strongSwan 类缺失，IKEv2 不可用");
            return false;
        }
    }

    @Override
    public void connect(@NonNull TunnelRequest request, @NonNull Callback callback) {
        activeCallback = callback;
        if (subscription == null) {
            subscription = binder.observe(this::dispatch);
        }
        VpnProfile profile = toProfile(request);
        callback.onConnecting();
        binder.connect(profile);
    }

    @Override
    public void disconnect() {
        if (activeCallback != null) {
            activeCallback.onDisconnecting();
        }
        binder.disconnect();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void release() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        activeCallback = null;
        connected = false;
    }

    // --------------------------------------------------------------- private

    private void dispatch(@NonNull VpnStateService.State state,
                          @NonNull VpnStateService.ErrorState error,
                          @NonNull org.strongswan.android.logic.imc.ImcState imc) {
        Callback cb = activeCallback;
        if (cb == null) {
            return;
        }
        switch (state) {
            case CONNECTING:
                connected = false;
                cb.onConnecting();
                break;
            case CONNECTED:
                connected = true;
                cb.onConnected();
                break;
            case DISCONNECTING:
                connected = false;
                cb.onDisconnecting();
                break;
            case DISABLED:
                boolean wasConnected = connected;
                connected = false;
                if (error != VpnStateService.ErrorState.NO_ERROR) {
                    cb.onError(mapError(error), error.name());
                } else if (wasConnected || activeCallback != null) {
                    cb.onDisconnected();
                }
                break;
            default:
                break;
        }
    }

    @NonNull
    private static ErrorCode mapError(@NonNull VpnStateService.ErrorState error) {
        switch (error) {
            case AUTH_FAILED:
                return ErrorCode.AUTH_FAILED;
            case PEER_AUTH_FAILED:
                return ErrorCode.PEER_AUTH_FAILED;
            case LOOKUP_FAILED:
                return ErrorCode.LOOKUP_FAILED;
            case UNREACHABLE:
                return ErrorCode.UNREACHABLE;
            case GENERIC_ERROR:
                return ErrorCode.NO_PROPOSAL;
            default:
                return ErrorCode.GENERIC;
        }
    }

    @NonNull
    private static VpnProfile toProfile(@NonNull TunnelRequest request) {
        VpnProfile profile = new VpnProfile();
        profile.setName("client");
        profile.setVpnType(Constants.vpnType);
        profile.setGateway(request.getServer());
        profile.setPort(request.getPort() > 0 ? request.getPort() : 500);
        profile.setUsername(request.getUsername());
        profile.setPassword(request.getPassword());
        profile.setCert(request.getCertificate());
        // MTU 必须给死值，不能交给 strongSwan 用 1500：ESP 封装后有几十字节开销，
        // 隧道里 1500 字节的包发不出去，表现为"能连上、小请求正常、大一点的下载卡死"
        // （实测该隧道 1300 能通、1350 就丢）。1280 是 IPv6 的最小 MTU，任何链路上都安全。
        profile.setMTU(request.getMtu() > 0 ? request.getMtu() : DEFAULT_MTU);
        com.openapi.domain.model.SplitTunnelingPolicy policy = request.getSplitTunneling();
        if (!policy.isAllThrough()) {
            profile.setSplitMode(policy.getMode() == com.openapi.domain.model.SplitTunnelingPolicy.Mode.INCLUDE
                    ? "include" : "exclude");
            profile.setSplitPackages(new java.util.LinkedHashSet<>(policy.getPackages()));
        }
        return profile;
    }
}
