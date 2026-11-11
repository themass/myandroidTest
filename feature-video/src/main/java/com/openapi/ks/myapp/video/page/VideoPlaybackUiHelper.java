package com.openapi.ks.myapp.video.page;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.feature.video.R;
import com.openapi.ks.myapp.constant.Constants;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;

/**
 * GSY 列表与详情共用的播放失败提示与重试入口。
 */
public final class VideoPlaybackUiHelper {

    private VideoPlaybackUiHelper() {
    }

    public static void onGsyPlaybackError(android.content.Context context, String url) {
        LogUtil.i("GSY onPlayError url=" + url);
        ToastUtil.showShort(R.string.video_playback_failed);
    }

    /**
     * 首次进入全屏播放页时提示常见手势（仅展示一次）。
     */
    public static void maybeShowFirstPlaybackGestureHint(AppCompatActivity activity, View anchor) {
        if (activity == null || anchor == null) {
            return;
        }
        if (PreferenceUtils.getPrefBoolean(activity, Constants.PLAYER_GESTURE_HINT_SHOWN, false)) {
            return;
        }
        anchor.post(() -> {
            if (activity.isFinishing()) {
                return;
            }
            try {
                Snackbar.make(anchor, R.string.player_gesture_hint, Snackbar.LENGTH_LONG).show();
            } catch (Throwable t) {
                LogUtil.e(t);
            }
            PreferenceUtils.setPrefBoolean(activity, Constants.PLAYER_GESTURE_HINT_SHOWN, true);
        });
    }

    public static void onGsyDetailPlaybackError(GSYBaseVideoPlayer player, int what, int extra) {
        LogUtil.i("GSY detail onError what=" + what + " extra=" + extra);
        ToastUtil.showShort(R.string.video_playback_failed);
        View anchor = player;
        if (anchor == null || anchor.getWindowToken() == null) {
            return;
        }
        try {
            Snackbar.make(anchor, R.string.video_playback_failed_retry, Snackbar.LENGTH_LONG)
                    .setAction(R.string.video_playback_retry_action, v -> player.startPlayLogic())
                    .show();
        } catch (Throwable ignored) {
            ToastUtil.showShort(R.string.video_playback_failed);
        }
    }
}
