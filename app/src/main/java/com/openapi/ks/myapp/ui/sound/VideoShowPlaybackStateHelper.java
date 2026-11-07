package com.openapi.ks.myapp.ui.sound;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.ks.myapp.ui.gsy.DetailGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;

/**
 * 详情页进程回收时保存/恢复播放进度与是否在播状态（配合 GSY {@code setSeekOnStart}）。
 */
public final class VideoShowPlaybackStateHelper {

    private static final String KEY_POS_MS = "gsy_detail_pos_ms";
    private static final String KEY_WAS_PLAYING = "gsy_detail_was_playing";

    private VideoShowPlaybackStateHelper() {
    }

    public static void save(@Nullable DetailGSYVideoPlayer player, @NonNull Bundle outState) {
        if (player == null) {
            return;
        }
        try {
            int state = player.getCurrentState();
            boolean playing = state == GSYVideoView.CURRENT_STATE_PLAYING;
            outState.putLong(KEY_POS_MS, player.getCurrentPositionWhenPlaying());
            outState.putBoolean(KEY_WAS_PLAYING, playing);
        } catch (Throwable ignored) {
        }
    }

    /** 在 {@code setUp} 之后、{@code startPlayLogic} 之前调用。 */
    public static void prepareSeekRestore(@Nullable DetailGSYVideoPlayer player, @Nullable Bundle saved) {
        if (player == null || saved == null) {
            return;
        }
        long pos = saved.getLong(KEY_POS_MS, -1L);
        if (pos > 500L) {
            player.setSeekOnStart(pos);
        }
    }

    /** 在 {@code startPlayLogic} 之后调用；此前若在暂停态，延迟一次暂停避免与起播竞态。 */
    public static void schedulePauseIfWasPaused(@Nullable DetailGSYVideoPlayer player, @Nullable Bundle saved) {
        if (player == null || saved == null) {
            return;
        }
        if (saved.getBoolean(KEY_WAS_PLAYING, true)) {
            return;
        }
        player.postDelayed(() -> {
            try {
                player.onVideoPause();
            } catch (Throwable ignored) {
            }
        }, 450L);
    }
}
