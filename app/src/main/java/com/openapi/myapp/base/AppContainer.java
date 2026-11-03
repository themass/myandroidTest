package com.openapi.myapp.base;

import android.content.Context;

import com.openapi.data.vpn.CharonVpnSessionRepository;
import com.openapi.domain.repository.VpnSessionRepository;
import com.openapi.myapp.data.SessionRecorder;

/**
 * 进程级依赖容器（轻量 ServiceLocator）。
 * <p>
 * 现阶段只托管 VPN 会话仓库。后续 ServerRepository / UserRepository 也在这里暴露，
 * 等依赖图复杂到需要作用域时再换 Hilt，替换面只有这个类。
 */
public final class AppContainer {

    private static volatile VpnSessionRepository vpnSession;

    private AppContainer() {
    }

    /** 在 {@code Application.onCreate()} 中调用一次。 */
    public static void init(Context context) {
        if (vpnSession == null) {
            synchronized (AppContainer.class) {
                if (vpnSession == null) {
                    CharonVpnSessionRepository repo = new CharonVpnSessionRepository();
                    repo.start(context.getApplicationContext());
                    // 连接会话记录（时长与流量）：挂在状态流上，不改各个切换点
                    repo.observeState().observeForever(
                            new SessionRecorder(context, repo.observeProtocol()));
                    vpnSession = repo;
                }
            }
        }
    }

    public static VpnSessionRepository vpnSession() {
        VpnSessionRepository repo = vpnSession;
        if (repo == null) {
            throw new IllegalStateException("AppContainer.init() 尚未调用");
        }
        return repo;
    }
}
