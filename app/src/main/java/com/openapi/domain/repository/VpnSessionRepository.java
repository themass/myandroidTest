package com.openapi.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.myapp.bean.vo.VpnProfile;

import java.util.List;

/**
 * VPN 会话的唯一事实来源。
 * <p>
 * UI 只允许通过这里读状态、发指令。它同时承担"协议选择"的职责：上层给出
 * 协议与参数，由实现找到对应引擎并驱动；切换协议必须先停旧引擎再起新引擎，
 * 因为 Android 同一时刻只允许一个 VpnService 持有 TUN。
 */
public interface VpnSessionRepository {

    /** 可观察的会话状态；进程内唯一，跨 Fragment/Activity 共享同一份。 */
    @NonNull
    LiveData<VpnSessionState> observeState();

    /** 当前快照，供非 UI 线程读取。 */
    @NonNull
    VpnSessionState current();

    /** 当前设备上可用的协议，供 UI 展示与自动选优。 */
    @NonNull
    List<TunnelProtocol> availableProtocols();

    /**
     * 用指定协议发起连接。若当前有其他协议的连接，会先断开再切换。
     *
     * @param protocol 目标协议
     * @param request  协议无关的连接参数
     * @return 指令是否被接受
     */
    boolean connect(@NonNull TunnelProtocol protocol, @NonNull TunnelRequest request);

    /** 断开当前连接。 */
    boolean disconnect();

    /** 应用启动时调用一次。 */
    void start(@NonNull android.content.Context context);

    /** 兼容旧调用：用 IKEv2 与既有 profile 连接。 */
    @Deprecated
    boolean connect(@Nullable VpnProfile profile);
}
