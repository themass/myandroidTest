package com.openapi.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.domain.model.TunnelProtocol;

/**
 * 用户的协议偏好。
 * <p>
 * 存储的是协议 id 而不是枚举序号——枚举顺序将来若调整，历史值仍然有效。
 * {@code null} 表示"自动"，由 {@code ProtocolSelector} 决定。
 */
public final class ProtocolPreferences {

    private static final String KEY_PREFERRED_PROTOCOL = "PREFERRED_TUNNEL_PROTOCOL";

    private ProtocolPreferences() {
    }

    @Nullable
    public static TunnelProtocol getPreferred(@NonNull Context context) {
        String id = PreferenceUtils.getPrefString(context, KEY_PREFERRED_PROTOCOL, null);
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (TunnelProtocol p : TunnelProtocol.values()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static void setPreferred(@NonNull Context context, @Nullable TunnelProtocol protocol) {
        PreferenceUtils.setPrefString(context, KEY_PREFERRED_PROTOCOL,
                protocol == null ? "" : protocol.getId());
    }

    /** 是否处于自动选择模式。 */
    public static boolean isAuto(@NonNull Context context) {
        return getPreferred(context) == null;
    }
}
