package com.openai.commonview.translation;

import android.view.ViewGroup;

/**
 * 由播放页实现：提供字幕浮层父容器、填充请求参数、拿到注册表。
 * 音频页将当前曲 {@code file} 写入 {@link VideoTranslationRequest.Builder#setVideoUrl}，
 * 并建议附带 {@link VideoTranslationRequest#EXTRA_MEDIA_KIND}={@link VideoTranslationRequest#MEDIA_KIND_AUDIO}。
 */
public interface TranslationOverlaySession {

    /** 一般为全屏播放器同级的 {@link android.widget.FrameLayout}，浮层盖在视频上。 */
    ViewGroup getOverlayParent();

    void fillRequest(VideoTranslationRequest.Builder builder);

    VideoTranslationRegistry registry();
}
