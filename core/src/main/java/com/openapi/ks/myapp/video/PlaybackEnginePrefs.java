package com.openapi.ks.myapp.video;

import android.content.Context;

import com.openapi.core.R;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.ks.myapp.constant.Constants;

/**
 * 播放引擎的偏好：选哪个内核、旧偏好迁移、名字。只读写偏好，不创建播放器，所以放在 :core，
 * 设置页（:feature-user）不需要依赖 :feature-video。真正应用引擎的代码仍在 PlaybackEngineHelper。
 */
public final class PlaybackEnginePrefs {

    public static final int ENGINE_EXO = 0;
    /** IJK + {@code gsyvideoplayer-ex_so} 带来的扩展解码/协议能力 */
    public static final int ENGINE_IJK_FFMPEG = 1;
    public static final int ENGINE_ALI = 2;
    /** 系统 MediaPlayer，格式支持最少、依赖最小 */
    public static final int ENGINE_SYSTEM = 3;
    /** 已移除 LibVLC；保留常量供旧偏好迁移 */
    private static final int LEGACY_ENGINE_VLC = 4;

    private static volatile boolean legacyMigrated;

    private PlaybackEnginePrefs() {
    }

    public static void migrateLegacyIfNeeded(Context context) {
        if (legacyMigrated) {
            return;
        }
        synchronized (PlaybackEnginePrefs.class) {
            if (legacyMigrated) {
                return;
            }
            if (!PreferenceUtils.hasKey(context, Constants.PLAYBACK_ENGINE)) {
                boolean oldIjk = PreferenceUtils.getPrefBoolean(context, Constants.PLAYCORE_SWITCH, false);
                setEngine(context, oldIjk ? ENGINE_IJK_FFMPEG : ENGINE_EXO);
            } else {
                int saved = PreferenceUtils.getPrefInt(context, Constants.PLAYBACK_ENGINE, ENGINE_EXO);
                if (saved == LEGACY_ENGINE_VLC) {
                    setEngine(context, ENGINE_EXO);
                }
            }
            legacyMigrated = true;
        }
    }

    public static int getEngine(Context context) {
        migrateLegacyIfNeeded(context);
        int v = PreferenceUtils.getPrefInt(context, Constants.PLAYBACK_ENGINE, ENGINE_EXO);
        if (v < ENGINE_EXO || v > ENGINE_SYSTEM) {
            return ENGINE_EXO;
        }
        return v;
    }

    public static void setEngine(Context context, int engine) {
        int e = engine;
        if (e < ENGINE_EXO || e > ENGINE_SYSTEM) {
            e = ENGINE_EXO;
        }
        PreferenceUtils.setPrefInt(context, Constants.PLAYBACK_ENGINE, e);
        PreferenceUtils.setPrefBoolean(context, Constants.PLAYCORE_SWITCH, e == ENGINE_IJK_FFMPEG);
    }

    public static String engineLabel(Context context, int engine) {
        String[] names = context.getResources().getStringArray(R.array.playback_engine_names);
        if (engine >= 0 && engine < names.length) {
            return names[engine];
        }
        return names[0];
    }
}
