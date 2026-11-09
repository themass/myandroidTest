package com.openapi.ks.myapp.cast;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.SessionManager;

/**
 * 在无 GMS、华为设备、部分模拟器或未安装 Cast dynamite 模块时，
 * {@link CastContext#getSharedInstance(Context)} 会抛出 {@link RuntimeException}
 * （内含 {@code ModuleUnavailableException}）。统一在此吞掉异常并返回 null，避免 Activity 崩溃。
 */
public final class CastFrameworkUtil {

    private CastFrameworkUtil() {
    }

    @Nullable
    public static CastContext tryGetCastContext(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        try {
            return CastContext.getSharedInstance(context.getApplicationContext());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static SessionManager tryGetSessionManager(@Nullable Context context) {
        CastContext cc = tryGetCastContext(context);
        if (cc == null) {
            return null;
        }
        try {
            return cc.getSessionManager();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
