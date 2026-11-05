package com.openapi.data.vpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openapi.common.util.LogUtil;
import com.openapi.data.vpn.engine.Ikev2TunnelEngine;
import com.openapi.data.vpn.engine.WireGuardTunnelEngine;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.domain.repository.TunnelEngine;
import com.openapi.domain.repository.TunnelRequest;
import com.openapi.domain.repository.VpnSessionRepository;
import com.openapi.domain.usecase.ReconnectPolicy;
import com.openapi.myapp.bean.vo.VpnProfile;

import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.imc.ImcState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link VpnSessionRepository} 的唯一实现，同时是多协议的调度中心。
 * <p>
 * 架构要点：
 * <ul>
 *   <li><b>单一 VpnService 约束</b>：Android 同一时刻只允许一个 VpnService 持有 TUN。
 *       因此所有引擎串行使用，切换协议时先停旧引擎、等其释放后再起新引擎。</li>
 *   <li><b>状态归一</b>：无论底层是 IKEv2 还是 WireGuard，对 UI 都只暴露
 *       {@link VpnSessionState}，UI 不需要知道协议差异。</li>
 *   <li><b>IKEv2 额外绑定</b>：strongSwan 的状态来自 {@link VpnStateService}，
 *       这里保留绑定逻辑并作为 {@link Ikev2TunnelEngine.StateBinder} 注入。</li>
 * </ul>
 */
public final class CharonVpnSessionRepository implements VpnSessionRepository {

    private static final String TAG = "VpnSessionRepo";
    private static final long REBIND_DELAY_MS = 1000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<VpnSessionState> state =
            new MutableLiveData<>(VpnSessionState.IDLE);
    private final MutableLiveData<TunnelProtocol> activeProtocol =
            new MutableLiveData<>(TunnelProtocol.IKEV2);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final TunnelEngineRegistry registry = new TunnelEngineRegistry();
    private final com.openapi.domain.usecase.ProtocolSelector selector =
            new com.openapi.domain.usecase.ProtocolSelector();
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy();
    private final com.openapi.domain.usecase.ConnectionMetricsRecorder metrics =
            new com.openapi.domain.usecase.ConnectionMetricsRecorder();
    /** 最近一次已完成连接的指标，供诊断与上报读取。 */
    @Nullable
    private volatile com.openapi.domain.usecase.ConnectionMetrics lastMetrics;

    /** 用于断线重连：记住最近一次成功发起连接时用的协议与参数。 */
    @Nullable
    private TunnelProtocol lastProtocol;
    @Nullable
    private TunnelRequest lastRequest;
    @Nullable
    private Runnable pendingRetry;
    /** 用户主动断开时置位，避免把它当成"故障"去触发自动重连。 */
    private volatile boolean userInitiatedDisconnect;

    @Nullable
    private Context appContext;
    @Nullable
    private VpnStateService service;
    @Nullable
    private VpnProfile pendingProfile;
    private boolean bindingRequested;

    @Nullable
    private TunnelEngine activeEngine;

    private final VpnStateService.VpnStateListener listener = this::publishFromService;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            LogUtil.i(TAG + ": onServiceConnected");
            service = ((VpnStateService.LocalBinder) binder).getService();
            service.registerListener(listener);
            publishFromService();

            VpnProfile pending = pendingProfile;
            pendingProfile = null;
            if (pending != null) {
                service.connect(pending);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtil.i(TAG + ": onServiceDisconnected");
            service = null;
            state.setValue(VpnSessionState.IDLE);
            scheduleRebind();
        }
    };

    // ---------------------------------------------------------------- public

    @Override
    public void start(@NonNull Context context) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        appContext = context.getApplicationContext();
        registry.register(new Ikev2TunnelEngine(new Ikev2StateBinder()));
        registry.register(new WireGuardTunnelEngine(appContext));
        registry.register(new com.openapi.data.vpn.engine.SingBoxTunnelEngine(appContext));
        requestBind();
    }

    @NonNull
    @Override
    public LiveData<VpnSessionState> observeState() {
        return state;
    }

    /** 当前协议的观察点，供 UI 显示协议标签。 */
    @NonNull
    public LiveData<TunnelProtocol> observeProtocol() {
        return activeProtocol;
    }

    @NonNull
    @Override
    public VpnSessionState current() {
        VpnSessionState value = state.getValue();
        return value == null ? VpnSessionState.IDLE : value;
    }

    @NonNull
    @Override
    public List<TunnelProtocol> availableProtocols() {
        return registry.availableProtocols();
    }

    @Override
    public boolean connect(@NonNull TunnelProtocol protocol, @NonNull TunnelRequest request) {
        TunnelEngine engine = registry.find(protocol);
        if (engine == null || !engine.isAvailable()) {
            LogUtil.e(TAG + ": 协议不可用: " + protocol);
            return false;
        }
        cancelPendingRetry();
        userInitiatedDisconnect = false;
        lastProtocol = protocol;
        lastRequest = request;
        // 切换协议：同一个 VpnService 只能有一个 TUN，必须先停旧引擎。
        TunnelEngine current = activeEngine;
        if (current != null && current != engine && current.isConnected()) {
            LogUtil.i(TAG + ": 切换协议 " + current.protocol() + " -> " + protocol);
            current.disconnect();
        }
        activeEngine = engine;
        activeProtocol.setValue(protocol);
        // 通知栏按钮在非 IKEv2 时转交给这里，断真正在跑的那个引擎。
        org.strongswan.android.logic.CharonVpnService.activeEngineDisconnect =
                CharonVpnSessionRepository.this::disconnect;
        // 告诉 CharonVpnService：这次连接是不是它自己（IKEv2）。不是的话让它
        // 别刷自己的通知——否则会跟这次实际使用的协议引擎自己的通知同时出现，
        // 且内容是陈旧的（见 CharonVpnService.nonIkev2ProtocolActive 的注释）。
        org.strongswan.android.logic.CharonVpnService.nonIkev2ProtocolActive =
                protocol != TunnelProtocol.IKEV2;
        if (protocol != TunnelProtocol.IKEV2) {
            // 立刻撤掉 IKEv2 那条陈旧通知，不等它下次 stateChanged 自己发现。
            // 否则用户会同时看到"click to disconn / Connecting..."两条，且前者
            // 永远停在"连接中"（VpnStateService 的状态根本没被这次连接更新过）。
            org.strongswan.android.logic.CharonVpnService.suppressIkev2Notification();
        }
        metrics.begin(protocol.getId());
        engine.connect(request, engineCallback);
        return true;
    }

    /**
     * 自动选择协议并连接。
     * <p>
     * 在可用协议里按 {@link com.openapi.domain.usecase.ProtocolSelector} 的策略挑一个；
     * 用户显式指定的协议若当前不可用或近期频繁失败，会回退到自动策略。
     *
     * @param request   协议无关参数
     * @param preferred 用户偏好，可为 null
     * @return 实际选中的协议；无可用协议时返回 null
     */
    @Nullable
    public TunnelProtocol connectAuto(@NonNull TunnelRequest request,
                                      @Nullable TunnelProtocol preferred) {
        TunnelProtocol chosen = selector.select(registry.availableProtocols(), preferred);
        if (chosen == null) {
            LogUtil.e(TAG + ": 没有可用协议");
            return null;
        }
        LogUtil.i(TAG + ": 自动选中协议 " + chosen);
        reconnectPolicy.reset();
        connect(chosen, request);
        return chosen;
    }

    @Deprecated
    @Override
    public boolean connect(@Nullable VpnProfile profile) {
        if (profile == null) {
            return false;
        }
        // 这条路径本身就是 IKEv2（唯一调用方是"回退 IKEv2"分支），让 CharonVpnService
        // 恢复显示自己的通知。
        org.strongswan.android.logic.CharonVpnService.nonIkev2ProtocolActive = false;
        TunnelEngine engine = registry.find(TunnelProtocol.IKEV2);
        if (engine instanceof Ikev2TunnelEngine) {
            activeEngine = engine;
        }
        VpnStateService svc = service;
        if (svc != null) {
            svc.connect(profile);
            return true;
        }
        pendingProfile = profile;
        requestBind();
        return false;
    }

    @Override
    public boolean disconnect() {
        userInitiatedDisconnect = true;
        cancelPendingRetry();
        reconnectPolicy.reset();
        // 断开后把通知权还给 IKEv2（它一直是绑定状态，断开后应该显示"未连接"，
        // 而不是停留在"别的协议占用中"）。
        org.strongswan.android.logic.CharonVpnService.nonIkev2ProtocolActive = false;
        TunnelEngine engine = activeEngine;
        if (engine != null) {
            engine.disconnect();
            return true;
        }
        VpnStateService svc = service;
        if (svc == null) {
            return false;
        }
        svc.disconnect();
        return true;
    }

    private void cancelPendingRetry() {
        if (pendingRetry != null) {
            mainHandler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
    }

    // --------------------------------------------------------------- private

    /** 任意引擎的状态变化都归一到 {@link VpnSessionState} 并发布。 */
    private final TunnelEngine.Callback engineCallback = new TunnelEngine.Callback() {
        @Override
        public void onConnecting() {
            mainHandler.post(() -> state.setValue(new VpnSessionState(
                    VpnStateService.State.CONNECTING,
                    VpnStateService.ErrorState.NO_ERROR, ImcState.UNKNOWN, true)));
        }

        @Override
        public void onConnected() {
            TunnelProtocol p = activeProtocol.getValue();
            if (p != null) {
                selector.recordSuccess(p);
            }
            reconnectPolicy.reset();
            metrics.markConnected();
            mainHandler.post(() -> state.setValue(new VpnSessionState(
                    VpnStateService.State.CONNECTED,
                    VpnStateService.ErrorState.NO_ERROR, ImcState.UNKNOWN, true)));
        }

        @Override
        public void onDisconnecting() {
            mainHandler.post(() -> state.setValue(new VpnSessionState(
                    VpnStateService.State.DISCONNECTING,
                    VpnStateService.ErrorState.NO_ERROR, ImcState.UNKNOWN, true)));
        }

        @Override
        public void onDisconnected() {
            // 不能直接用 VpnSessionState.IDLE 这个静态常量——它的 bound 字段硬编码为
            // false（那个常量原本是为"IKEv2 的 AIDL 服务未绑定"设计的），而这里的
            // onDisconnecting/onConnected/onConnecting 等同级回调全都传 bound=true。
            // 用了 IDLE 常量会导致 toggleVpn() 里 current.isBound() 之后判断为 false，
            // 表现为"断开之后再点连接，提示 Try later please"——明明已经完全空闲了，
            // 却被当成"还没就绪"拦住。
            mainHandler.post(() -> state.setValue(new VpnSessionState(
                    VpnStateService.State.DISABLED,
                    VpnStateService.ErrorState.NO_ERROR, ImcState.UNKNOWN, true)));
        }

        @Override
        public void onError(@NonNull TunnelEngine.ErrorCode errorCode, @Nullable String reason) {
            LogUtil.e(TAG + ": 引擎错误 " + errorCode + " " + reason);
            TunnelProtocol p = activeProtocol.getValue();
            if (p != null && errorCode != TunnelEngine.ErrorCode.CONFIG_INVALID) {
                selector.recordFailure(p);
            }

            if (userInitiatedDisconnect || !isRecoverable(errorCode)) {
                // 用户主动断开，或者是重试了也没用的错误（账号密码错、配置非法）：
                // 不自动重试，直接把错误交给 UI。
                recordMetrics(false, errorCode.name());
                reconnectPolicy.reset();
                VpnStateService.ErrorState mapped = mapError(errorCode);
                mainHandler.post(() -> state.setValue(new VpnSessionState(
                        VpnStateService.State.DISABLED, mapped, ImcState.UNKNOWN, true)));
                return;
            }

            if (reconnectPolicy.shouldRetry() && lastProtocol != null && lastRequest != null) {
                long delay = reconnectPolicy.nextDelayMillis();
                LogUtil.i(TAG + ": 第 " + reconnectPolicy.attemptCount()
                        + " 次自动重连，" + delay + "ms 后发起");
                // 停留在"连接中"而不是立刻报错，让网络抖动对用户无感。
                mainHandler.post(() -> state.setValue(new VpnSessionState(
                        VpnStateService.State.CONNECTING,
                        VpnStateService.ErrorState.NO_ERROR, ImcState.UNKNOWN, true)));
                cancelPendingRetry();
                TunnelProtocol retryProtocol = lastProtocol;
                TunnelRequest retryRequest = lastRequest;
                pendingRetry = () -> {
                    pendingRetry = null;
                    if (!userInitiatedDisconnect) {
                        connectRetry(retryProtocol, retryRequest);
                    }
                };
                mainHandler.postDelayed(pendingRetry, delay);
            } else {
                // 重试次数用尽：这才是"真失败"，交给 UI 并清空重连状态。
                recordMetrics(false, errorCode.name());
                reconnectPolicy.reset();
                VpnStateService.ErrorState mapped = mapError(errorCode);
                mainHandler.post(() -> state.setValue(new VpnSessionState(
                        VpnStateService.State.DISABLED, mapped, ImcState.UNKNOWN, true)));
            }
        }
    };

    /** 是否值得自动重试。鉴权/配置类错误重试了也是同样的结果，不浪费电量去试。 */
    private static boolean isRecoverable(@NonNull TunnelEngine.ErrorCode code) {
        switch (code) {
            case AUTH_FAILED:
            case PEER_AUTH_FAILED:
            case CONFIG_INVALID:
                return false;
            default:
                return true;
        }
    }

    /**
     * 退避重连专用的连接入口：与 {@link #connect(TunnelProtocol, TunnelRequest)} 的区别是
     * 不重置 {@link #reconnectPolicy}、不清 {@link #userInitiatedDisconnect}——
     * 这正是一次"同一会话内的恢复尝试"，不是一次新连接。
     */
    private void connectRetry(@NonNull TunnelProtocol protocol, @NonNull TunnelRequest request) {
        TunnelEngine engine = registry.find(protocol);
        if (engine == null || !engine.isAvailable()) {
            LogUtil.e(TAG + ": 重连时协议不可用: " + protocol);
            return;
        }
        activeEngine = engine;
        activeProtocol.setValue(protocol);
        metrics.noteRetry();
        engine.connect(request, engineCallback);
    }

    private void recordMetrics(boolean succeeded, @Nullable String errorCode) {
        if (!metrics.isActive()) {
            return;
        }
        com.openapi.domain.usecase.ConnectionMetrics m = metrics.finish(succeeded, errorCode);
        if (m != null) {
            lastMetrics = m;
            LogUtil.i(TAG + ": 连接指标 " + m);
        }
    }

    /** 最近一次连接的质量指标；未产生过连接时为 null。 */
    @Nullable
    public com.openapi.domain.usecase.ConnectionMetrics getLastMetrics() {
        return lastMetrics;
    }

    @NonNull
    private static VpnStateService.ErrorState mapError(@NonNull TunnelEngine.ErrorCode code) {
        switch (code) {
            case AUTH_FAILED:
                return VpnStateService.ErrorState.AUTH_FAILED;
            case PEER_AUTH_FAILED:
                return VpnStateService.ErrorState.PEER_AUTH_FAILED;
            case LOOKUP_FAILED:
                return VpnStateService.ErrorState.LOOKUP_FAILED;
            case UNREACHABLE:
                return VpnStateService.ErrorState.UNREACHABLE;
            default:
                return VpnStateService.ErrorState.GENERIC_ERROR;
        }
    }

    private void requestBind() {
        if (appContext == null || bindingRequested) {
            return;
        }
        bindingRequested = true;
        boolean ok = appContext.bindService(
                new Intent(appContext, VpnStateService.class),
                connection,
                Context.BIND_AUTO_CREATE);
        if (!ok) {
            bindingRequested = false;
            scheduleRebind();
        }
    }

    private void scheduleRebind() {
        bindingRequested = false;
        mainHandler.removeCallbacks(this::requestBind);
        mainHandler.postDelayed(this::requestBind, REBIND_DELAY_MS);
    }

    /** 从 strongSwan 服务读取快照并发布；调用点必须已在主线程。 */
    private void publishFromService() {
        VpnStateService svc = service;
        if (svc == null) {
            state.setValue(VpnSessionState.IDLE);
            return;
        }
        // 非 IKEv2 协议激活时，状态以引擎回调为准，避免两个来源互相覆盖。
        TunnelEngine engine = activeEngine;
        if (engine != null && engine.protocol() != TunnelProtocol.IKEV2) {
            return;
        }
        VpnSessionState next = new VpnSessionState(
                svc.getState(), svc.getErrorState(), svc.getImcState(), true);
        if (!next.equals(state.getValue())) {
            state.setValue(next);
        }
    }

    /** 把 strongSwan 的状态服务适配成 IKEv2 引擎所需的订阅接口。 */
    private final class Ikev2StateBinder implements Ikev2TunnelEngine.StateBinder {
        @NonNull
        @Override
        public Ikev2TunnelEngine.Subscription observe(@NonNull Ikev2TunnelEngine.StateObserver observer) {
            Ikev2TunnelEngine.StateObserver target = observer;
            VpnStateService svc = service;
            if (svc != null) {
                svc.registerListener(() -> {
                    VpnStateService s = service;
                    if (s != null) {
                        target.onState(s.getState(), s.getErrorState(), s.getImcState());
                    }
                });
            }
            return () -> {
                // 监听随服务生命周期回收，这里无需额外动作。
            };
        }

        @Override
        public void connect(@NonNull VpnProfile profile) {
            CharonVpnSessionRepository.this.connect(profile);
        }

        @Override
        public void disconnect() {
            VpnStateService svc = service;
            if (svc != null) {
                svc.disconnect();
            }
        }
    }
}
