package com.openapi.domain.usecase;

/**
 * 断线重连的退避策略。
 * <p>
 * 稳定性优先于速度：网络抖动应当无感恢复，而不是让用户看到一次"连接失败"。
 * 策略本身协议无关、不依赖 Android，可单独单元测试。
 * <p>
 * 退避序列：2s、4s、8s、16s、30s（封顶），与常见移动网络抖动恢复窗口匹配——
 * 多数 Wi-Fi/4G 切换在 10 秒内恢复，不应该在这之前就放弃。
 */
public final class ReconnectPolicy {

    private static final long[] BACKOFF_MS = {2_000, 4_000, 8_000, 16_000, 30_000};
    /** 超过这个次数不再自动重试，必须用户手动触发。避免电量被无限重试耗尽。 */
    private static final int MAX_ATTEMPTS = 8;

    private int attempt;

    /** 本次失败是否应当重试；超过上限则认为是"真失败"，需要上报给用户。 */
    public boolean shouldRetry() {
        return attempt < MAX_ATTEMPTS;
    }

    /** 下一次重试前应等待的毫秒数，并消耗一次尝试计数。 */
    public long nextDelayMillis() {
        long delay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        attempt++;
        return delay;
    }

    public int attemptCount() {
        return attempt;
    }

    /** 连接成功或用户主动重新发起连接时调用，退避计数清零。 */
    public void reset() {
        attempt = 0;
    }
}
