package com.openapi.speech.subtitle.api;

/**
 * 可插拔的视频语音→文字→翻译链路（百度、火山、自建服务等各自实现）。
 * <p>
 * 典型实现：根据 {@link VideoTranslationRequest#getVideoUrl()} 拉流/送 URL 到 ASR，
 * 再调用机器翻译得到中文；{@link #getProviderId()} 用于注册与切换。
 */
public interface VideoTranslationProvider {

    /** 唯一标识，如 "baidu"、"volcano"。 */
    String getProviderId();

    /**
     * 执行翻译；须在完成后调用 callback（可在后台线程）。
     */
    void translate(VideoTranslationRequest request, VideoTranslationCallback callback);
}
