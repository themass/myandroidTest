package com.openapi.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 记录一次连接会话的质量指标。
 * <p>
 * 使用方式：
 * <pre>
 *   recorder.begin("ikev2");        // 用户发起连接
 *   ... 引擎回调 ...
 *   recorder.markConnected();       // 进入 CONNECTED，记录耗时
 *   ConnectionMetrics m = recorder.finish(true, null);
 * </pre>
 * 非线程安全，调用方需保证在同一个线程（通常是主线程）使用。
 */
public final class ConnectionMetricsRecorder {

    @Nullable
    private String protocol;
    private long startedAt;
    private long connectedAt = -1;
    private int attempt;

    /** 开始一次新的连接会话（用户主动发起）。 */
    public void begin(@NonNull String protocol) {
        this.protocol = protocol;
        this.startedAt = System.currentTimeMillis();
        this.connectedAt = -1;
        this.attempt = 0;
    }

    /** 自动重连时递增尝试计数，不重置起点——耗时应从用户最初点击算起。 */
    public void noteRetry() {
        attempt++;
    }

    /** 记录成功连上的时刻。 */
    public void markConnected() {
        if (connectedAt < 0) {
            connectedAt = System.currentTimeMillis();
        }
    }

    /**
     * 结束本次会话并产出指标快照。
     *
     * @param succeeded 最终是否成功
     * @param errorCode 失败时的错误分类名
     */
    @Nullable
    public ConnectionMetrics finish(boolean succeeded, @Nullable String errorCode) {
        String p = protocol;
        if (p == null) {
            return null;
        }
        long handshake = connectedAt > 0 ? connectedAt - startedAt : -1;
        ConnectionMetrics m = new ConnectionMetrics(p, handshake, succeeded, errorCode, attempt + 1);
        protocol = null;
        return m;
    }

    /** 当前是否处于一次会话中。 */
    public boolean isActive() {
        return protocol != null;
    }
}
