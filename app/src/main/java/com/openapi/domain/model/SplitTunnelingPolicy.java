package com.openapi.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 分应用代理策略（"选择性智能代理"的第一层）。
 * <p>
 * Android 的 {@code VpnService.Builder} 原生支持按包名纳入/排除，
 * WireGuard 的 {@code Interface.Builder} 同样提供 include/exclude。
 * 这一层不依赖具体协议，因此定义在 domain 层。
 * <p>
 * 注意语义差异：
 * <ul>
 *   <li>{@link Mode#EXCLUDE}：除名单外的应用都走代理（默认模式，适合"只放过银行/内网"）；</li>
 *   <li>{@link Mode#INCLUDE}：只有名单内的应用走代理（适合"只让特定应用翻墙"）。</li>
 * </ul>
 * 系统层面二者互斥，同一个 Builder 上不能既 include 又 exclude。
 */
public final class SplitTunnelingPolicy {

    public enum Mode {
        /** 除名单外全部走代理。 */
        EXCLUDE,
        /** 仅名单内的应用走代理。 */
        INCLUDE
    }

    public static final SplitTunnelingPolicy ALL_THROUGH = new SplitTunnelingPolicy(
            Mode.EXCLUDE, Collections.<String>emptySet());

    private final Mode mode;
    private final Set<String> packages;

    private SplitTunnelingPolicy(@NonNull Mode mode, @NonNull Set<String> packages) {
        this.mode = mode;
        this.packages = Collections.unmodifiableSet(new LinkedHashSet<>(packages));
    }

    @NonNull
    public static SplitTunnelingPolicy of(@NonNull Mode mode, @Nullable Set<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return ALL_THROUGH;
        }
        return new SplitTunnelingPolicy(mode, packages);
    }

    @NonNull
    public Mode getMode() {
        return mode;
    }

    @NonNull
    public Set<String> getPackages() {
        return packages;
    }

    public boolean isAllThrough() {
        return packages.isEmpty();
    }

    /** 该应用是否应走隧道。 */
    public boolean shouldTunnel(@NonNull String packageName) {
        if (packages.isEmpty()) {
            return true;
        }
        boolean listed = packages.contains(packageName);
        return mode == Mode.INCLUDE ? listed : !listed;
    }
}
