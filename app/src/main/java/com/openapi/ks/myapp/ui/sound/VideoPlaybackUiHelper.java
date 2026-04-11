package com.openapi.ks.myapp.ui.sound;

import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openapi.ks.chat.R;
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
