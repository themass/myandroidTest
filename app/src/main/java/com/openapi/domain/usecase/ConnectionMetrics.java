package com.openapi.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次连接尝试的质量指标。
 * <p>
 * 采集目的：稳定性不能靠"用户没投诉"来判断。有了握手耗时与成败，
 * 才能回答"换了协议之后到底变稳了没有"这类问题。
 * <p>
 * 不可变；由 {@link ConnectionMetricsRecorder} 在连接过程中逐步快照。
 */
public final class ConnectionMetrics {

    /** 协议 id，如 "ikev2" / "wireguard"。 */
    @NonNull
    public final String protocol;
    /** 从发起连接到进入 CONNECTED 的耗时；未成功时为 -1。 */
    public final long handshakeMs;
    /** 是否成功连上过。 */
    public final boolean succeeded;
    /** 失败时的错误分类名；成功时为 null。 */
    @Nullable
    public final String errorCode;
    /** 这是本次会话内的第几次尝试（1 = 用户首次点击，>1 = 自动重连）。 */
    public final int attempt;

    public ConnectionMetrics(@NonNull String protocol, long handshakeMs,
                             boolean succeeded, @Nullable String errorCode, int attempt) {
        this.protocol = protocol;
        this.handshakeMs = handshakeMs;
        this.succeeded = succeeded;
        this.errorCode = errorCode;
        this.attempt = attempt;
    }

    @Override
    public String toString() {
        return "ConnectionMetrics{" + protocol
                + ", handshakeMs=" + handshakeMs
                + ", succeeded=" + succeeded
                + ", error=" + errorCode
                + ", attempt=" + attempt + '}';
    }
}
