package com.openapi.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.domain.model.TunnelProtocol;

/**
 * 协议偏好（已停用）。协议由 {@code ProtocolSelector} 按节点能力自动决定，用户不可选。
 */
public final class ProtocolPreferences {

    private ProtocolPreferences() {
    }

    /**
     * 协议始终自动选择，不读取用户偏好（产品设计：用户不选协议）。
     * 之前版本保存过的偏好也会被忽略。
     */
    @Nullable
    public static TunnelProtocol getPreferred(@NonNull Context context) {
        return null;
    }
}
