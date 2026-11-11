package com.openapi.ks.myapp.translation.api;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 在线流式字幕：从播放中的媒体解出 PCM，经 ASR 实时回传文本。
 * 由视频/音乐播放页实现；{@link com.openapi.core.view.view.MyFavoriteView} 点「译」时优先走此 Session。
 */
public interface RealtimeSubtitleSession {

    @NonNull
    ViewGroup getOverlayParent();

    /** 火山等密钥是否已在应用内配置。 */
    boolean isConfigured();

    /**
     * 开始送音频并识别；回调在主线程。
     *
     * @param playbackPositionMs 当前播放进度，用于从对应时间点开始解音频
     */
    void startStreaming(long playbackPositionMs, @NonNull RealtimeSubtitleCallbacks callbacks);

    /** 停止 WebSocket 与解音频线程。 */
    void stopStreaming();

    boolean isStreaming();
}
