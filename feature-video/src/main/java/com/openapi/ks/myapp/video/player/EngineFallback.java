package com.openapi.ks.myapp.video.player;

import android.content.Context;
import android.view.View;

import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.feature.video.R;
import com.openapi.ks.myapp.video.PlaybackEngineHelper;

/**
 * 播放出错时临时换内核重试一次（不写设置项），detach 时恢复用户原来的内核偏好。
 * 从 {@link DetailGSYVideoPlayer} 拆出，行为不变。
 */
final class EngineFallback {

    private static final int NONE = Integer.MIN_VALUE;

    private boolean tried;
    /** 自动切换内核重试前保存的用户偏好，detach 时恢复 */
    private int restoreAfterDetach = NONE;

    void resetForNewUrl() {
        tried = false;
    }

    /**
     * 当前片源失败时自动换内核重试一次：Exo ↔ IJK；阿里 / 系统 失败时先临时换 Exo。
     *
     * @param anchor    播放器视图，用于取 Context 和 post
     * @param originUrl 当前片源地址
     * @param retry     切换内核后在主线程执行的重试（release + setUp + start）
     * @return 是否已开始重试
     */
    boolean tryAlternate(View anchor, String originUrl, Runnable retry) {
        if (tried || originUrl == null || originUrl.isEmpty()) {
            return false;
        }
        Context context = anchor.getContext();
        Context app = context.getApplicationContext();
        int cur = PlaybackEngineHelper.getEngine(app);
        int alt = PlaybackEngineHelper.alternateEngineForPlaybackRetry(cur);
        if (alt == cur) {
            return false;
        }
        tried = true;
        restoreAfterDetach = cur;
        String from = PlaybackEngineHelper.engineLabel(app, cur);
        String to = PlaybackEngineHelper.engineLabel(app, alt);
        LogUtil.i("GSY playback engine fallback: " + from + " -> " + to + " url=" + originUrl);
        ToastUtil.showShort(context.getString(R.string.video_playback_retry_other_engine, from, to));
        PlaybackEngineHelper.applyEngineByIndex(app, alt);
        anchor.post(() -> {
            try {
                retry.run();
            } catch (Throwable t) {
                LogUtil.e(t);
            }
        });
        return true;
    }

    /** 播放器从窗口移除时，若刚才是临时换的内核，恢复用户原来的设置。 */
    void restoreIfTemporary(Context context) {
        if (restoreAfterDetach == NONE) {
            return;
        }
        int saved = restoreAfterDetach;
        restoreAfterDetach = NONE;
        try {
            PlaybackEngineHelper.applyEngineByIndex(context.getApplicationContext(), saved);
        } catch (Throwable t) {
            LogUtil.e(t);
        }
    }
}
