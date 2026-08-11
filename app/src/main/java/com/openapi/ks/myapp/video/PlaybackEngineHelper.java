package com.openapi.ks.myapp.video;

import android.content.Context;

import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.constant.Constants;
import com.shuyu.gsyvideoplayer.cache.CacheFactory;
import com.shuyu.gsyvideoplayer.cache.ProxyCacheManager;
import com.shuyu.aliplay.AliPlayerManager;
import com.shuyu.gsyvideoplayer.player.IjkPlayerManager;
import com.shuyu.gsyvideoplayer.player.PlayerFactory;
import com.shuyu.gsyvideoplayer.player.SystemPlayerManager;

import tv.danmaku.ijk.media.exo2.Exo2PlayerManager;
import tv.danmaku.ijk.media.exo2.ExoPlayerCacheManager;

/**
 * GSY 全局播放内核与缓存策略，对齐
 * <a href="https://github.com/CarGuo/GSYVideoPlayer">GSYVideoPlayer</a>：
 * Exo2、IJK（含 ex_so 扩展 FFmpeg）、AliPlayer、系统 MediaPlayer。
 */
public final class PlaybackEngineHelper {

    public static final int ENGINE_EXO = 0;
    /** IJK + {@code gsyvideoplayer-ex_so} 带来的扩展解码/协议能力 */
    public static final int ENGINE_IJK_FFMPEG = 1;
    public static final int ENGINE_ALI = 2;
    /** 系统 {@link android.media.MediaPlayer}，格式支持最少、依赖最小 */
    public static final int ENGINE_SYSTEM = 3;
    /** 已移除 LibVLC；保留常量供旧偏好迁移 */
    private static final int LEGACY_ENGINE_VLC = 4;

    private static volatile boolean legacyMigrated;

    private PlaybackEngineHelper() {
    }

    public static void migrateLegacyIfNeeded(Context context) {
        if (legacyMigrated) {
            return;
        }
        synchronized (PlaybackEngineHelper.class) {
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

    /**
     * 播放失败时尝试的另一内核：Exo ↔ IJK；阿里 / 系统 失败时先换 Exo（不写入用户设置，仅本次进程内
     * {@link #applyEngineByIndex} 生效）。
     */
    public static int alternateEngineForPlaybackRetry(int currentEngine) {
        switch (currentEngine) {
            case ENGINE_EXO:
                return ENGINE_IJK_FFMPEG;
            case ENGINE_IJK_FFMPEG:
                return ENGINE_EXO;
            case ENGINE_ALI:
            case ENGINE_SYSTEM:
            default:
                return ENGINE_EXO;
        }
    }

    /**
     * 按指定索引应用内核与缓存（不写 SharedPreferences），供失败自动重试等场景。
     */
    public static void applyEngineByIndex(Context context, int engine) {
        int e = engine;
        if (e < ENGINE_EXO || e > ENGINE_SYSTEM) {
            e = ENGINE_EXO;
        }
        try {
            switch (e) {
                case ENGINE_IJK_FFMPEG:
                    PlayerFactory.setPlayManager(IjkPlayerManager.class);
                    CacheFactory.setCacheManager(ProxyCacheManager.class);
                    break;
                case ENGINE_ALI:
                    PlayerFactory.setPlayManager(AliPlayerManager.class);
                    CacheFactory.setCacheManager(ProxyCacheManager.class);
                    break;
                case ENGINE_SYSTEM:
                    PlayerFactory.setPlayManager(SystemPlayerManager.class);
                    CacheFactory.setCacheManager(ProxyCacheManager.class);
                    break;
                case ENGINE_EXO:
                default:
                    PlayerFactory.setPlayManager(Exo2PlayerManager.class);
                    CacheFactory.setCacheManager(ExoPlayerCacheManager.class);
                    break;
            }
        } catch (Throwable t) {
            LogUtil.e(t);
            try {
                PlayerFactory.setPlayManager(Exo2PlayerManager.class);
                CacheFactory.setCacheManager(ExoPlayerCacheManager.class);
            } catch (Throwable t2) {
                LogUtil.e(t2);
            }
        }
    }

    public static void setEngine(Context context, int engine) {
        int e = engine;
        if (e < ENGINE_EXO || e > ENGINE_SYSTEM) {
            e = ENGINE_EXO;
        }
        PreferenceUtils.setPrefInt(context, Constants.PLAYBACK_ENGINE, e);
        PreferenceUtils.setPrefBoolean(context, Constants.PLAYCORE_SWITCH, e == ENGINE_IJK_FFMPEG);
    }

    /**
     * 按用户选择应用 {@link PlayerFactory} 与 {@link CacheFactory}。
     * Exo 使用 SimpleCache；其余内核使用代理缓存（与 GSY 文档一致，避免 Exo 缓存与非 Exo 内核混用问题）。
     */
    public static void applyEngine(Context context) {
        migrateLegacyIfNeeded(context);
        applyEngineByIndex(context, getEngine(context));
    }

    public static String engineLabel(Context context, int engine) {
        String[] names = context.getResources().getStringArray(R.array.playback_engine_names);
        if (engine >= 0 && engine < names.length) {
            return names[engine];
        }
        return names[0];
    }
}
