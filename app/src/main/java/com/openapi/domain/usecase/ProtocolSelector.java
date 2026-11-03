package com.openapi.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.domain.model.TunnelProtocol;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议自动选优。
 * <p>
 * 策略（按用户可感知收益排序）：
 * <ol>
 *   <li>优先用"智能"模式：在可用协议里挑一个既快又没被封锁的；</li>
 *   <li>可用性优先于速度——被判定为"最近频繁失败"的协议直接降到候选末位；</li>
 *   <li>同等条件下按 {@link TunnelProtocol} 声明顺序，
 *       即 WireGuard &gt; IKEv2（吞吐与耗电更优）。</li>
 * </ol>
 * 失败计数带时间衰减，避免一次网络抖动导致协议被永久拉黑。
 */
public final class ProtocolSelector {

    /** 失败记录的有效期；超过则视为过期，协议重新参与竞争。 */
    private static final long FAILURE_TTL_MS = 10 * 60 * 1000L;
    /** 连续失败达到该次数即视为不可用，排到最后。 */
    private static final int FAILURE_THRESHOLD = 2;

    private final Map<TunnelProtocol, FailureRecord> failures = new ConcurrentHashMap<>();

    /** 记录一次连接失败。 */
    public void recordFailure(@NonNull TunnelProtocol protocol) {
        FailureRecord r = failures.get(protocol);
        if (r == null) {
            r = new FailureRecord();
            failures.put(protocol, r);
        }
        r.count++;
        r.lastFailureAt = System.currentTimeMillis();
    }

    /** 记录一次连接成功，清零该协议的失败计数。 */
    public void recordSuccess(@NonNull TunnelProtocol protocol) {
        failures.remove(protocol);
    }

    /**
     * 从可用协议中挑一个最合适的。
     *
     * @param available 当前设备真正可用的协议
     * @param preferred 用户显式选择的协议；为 null 表示自动
     * @return 选中的协议；available 为空时返回 null
     */
    @Nullable
    public TunnelProtocol select(@NonNull List<TunnelProtocol> available,
                                 @Nullable TunnelProtocol preferred) {
        if (available.isEmpty()) {
            return null;
        }
        // 用户显式指定且确实可用时尊重用户选择。
        if (preferred != null && available.contains(preferred) && !isDeprioritized(preferred)) {
            return preferred;
        }
        TunnelProtocol best = null;
        int bestScore = Integer.MAX_VALUE;
        for (TunnelProtocol p : available) {
            int score = priorityOf(p) + penaltyOf(p);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** 协议基础优先级：数字越小越优先。 */
    private static int priorityOf(@NonNull TunnelProtocol p) {
        switch (p) {
            case WIREGUARD:
                return 0;
            case IKEV2:
                return 1;
            case SING_BOX:
                return 2;
            default:
                return 3;
        }
    }

    private boolean isDeprioritized(@NonNull TunnelProtocol p) {
        return penaltyOf(p) >= FAILURE_THRESHOLD;
    }

    /** 失败带来的惩罚分；过期记录不再计分。 */
    private int penaltyOf(@NonNull TunnelProtocol p) {
        FailureRecord r = failures.get(p);
        if (r == null) {
            return 0;
        }
        if (System.currentTimeMillis() - r.lastFailureAt > FAILURE_TTL_MS) {
            failures.remove(p);
            return 0;
        }
        return r.count >= FAILURE_THRESHOLD ? FAILURE_THRESHOLD : r.count;
    }

    private static final class FailureRecord {
        int count;
        long lastFailureAt;
    }
}
