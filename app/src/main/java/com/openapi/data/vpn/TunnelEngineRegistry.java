package com.openapi.data.vpn;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.repository.TunnelEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 隧道引擎注册表。
 * <p>
 * 新增协议的接入点只有这里：实现 {@link TunnelEngine}，在 {@link #register} 注册，
 * UI 与选路逻辑自动可见。未注册或 {@code isAvailable()} 为 false 的协议会被
 * {@link #availableEngines()} 过滤掉，不会出现在用户可选列表里。
 */
public final class TunnelEngineRegistry {

    private final Map<TunnelProtocol, TunnelEngine> engines =
            new EnumMap<>(TunnelProtocol.class);

    public void register(@NonNull TunnelEngine engine) {
        engines.put(engine.protocol(), engine);
    }

    @Nullable
    public TunnelEngine find(@NonNull TunnelProtocol protocol) {
        return engines.get(protocol);
    }

    /**
     * 当前设备上真正可用的引擎，按协议声明的顺序返回。
     * 自动选优只会在这批里面挑。
     */
    @NonNull
    public List<TunnelEngine> availableEngines() {
        List<TunnelEngine> result = new ArrayList<>();
        for (TunnelProtocol p : TunnelProtocol.values()) {
            TunnelEngine e = engines.get(p);
            if (e != null && e.isAvailable()) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public List<TunnelProtocol> availableProtocols() {
        List<TunnelProtocol> result = new ArrayList<>();
        for (TunnelEngine e : availableEngines()) {
            result.add(e.protocol());
        }
        return result;
    }

    public void releaseAll() {
        for (TunnelEngine e : engines.values()) {
            e.release();
        }
        engines.clear();
    }
}
