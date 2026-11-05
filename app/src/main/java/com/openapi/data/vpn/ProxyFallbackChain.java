package com.openapi.data.vpn;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.data.remote.ProxyLinkSelector;

import java.util.Collections;
import java.util.List;

/**
 * 官方代理节点的候选链：按优先级依次尝试，前一个连不上就换下一个。
 * <p>
 * 只负责顺序和进度，不接触网络或 UI。连接本身由 {@link Connector} 完成，
 * 这样排序和边界行为可以在 JVM 上直接测试。
 */
public final class ProxyFallbackChain implements CharonVpnSessionRepository.ProxyFallback {

    /** 真正发起一次连接。 */
    public interface Connector {
        void connect(@NonNull ProxyLinkSelector.Candidate candidate);
    }

    private final List<ProxyLinkSelector.Candidate> candidates;
    private final Connector connector;
    /** 当前正在尝试的候选下标；-1 表示还没开始。 */
    private int index = -1;

    public ProxyFallbackChain(@NonNull List<ProxyLinkSelector.Candidate> candidates,
                              @NonNull Connector connector) {
        this.candidates = Collections.unmodifiableList(candidates);
        this.connector = connector;
    }

    @Override
    public boolean hasNext() {
        return index + 1 < candidates.size();
    }

    @Override
    public void connectNext() {
        if (!hasNext()) {
            return;
        }
        index++;
        connector.connect(candidates.get(index));
    }

    /** 当前尝试的候选；还没开始或已经用完返回 null。 */
    @Nullable
    public ProxyLinkSelector.Candidate current() {
        return index >= 0 && index < candidates.size() ? candidates.get(index) : null;
    }

    public int size() {
        return candidates.size();
    }
}
