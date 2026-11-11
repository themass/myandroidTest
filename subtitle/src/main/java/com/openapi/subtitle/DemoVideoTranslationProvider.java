package com.openapi.subtitle;


import com.openapi.subtitle.api.VideoTranslationCallback;
import com.openapi.subtitle.api.VideoTranslationProvider;
import com.openapi.subtitle.api.VideoTranslationRequest;
import com.openapi.subtitle.api.VideoTranslationResult;

/**
 * 本地演示：不请求网络，用于联调 UI。正式环境请换 {@link StubBaiduVideoTranslationProvider} 等实现。
 */
public class DemoVideoTranslationProvider implements VideoTranslationProvider {

    public static final String ID = "demo";

    @Override
    public String getProviderId() {
        return ID;
    }

    @Override
    public void translate(VideoTranslationRequest request, VideoTranslationCallback callback) {
        String url = request.getVideoUrl();
        String kind = request.getExtras().get(VideoTranslationRequest.EXTRA_MEDIA_KIND);
        boolean audio = VideoTranslationRequest.MEDIA_KIND_AUDIO.equals(kind);
        String text = "【演示模式】此处为占位译文。\n\n"
                + "接入说明：实现 VideoTranslationProvider，在 VideoTranslationInstaller 中 register 并 setActiveProviderId。\n\n"
                + (audio ? "当前为音频流（ASR/翻译）：\n" : "当前媒体地址：\n")
                + url;
        callback.onSuccess(new VideoTranslationResult.Builder()
                .setTranslatedText(text)
                .setProviderId(ID)
                .build());
    }
}
