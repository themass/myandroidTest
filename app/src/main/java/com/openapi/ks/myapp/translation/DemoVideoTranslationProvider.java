package com.openapi.ks.myapp.translation;

import com.openai.commonview.translation.VideoTranslationCallback;
import com.openai.commonview.translation.VideoTranslationProvider;
import com.openai.commonview.translation.VideoTranslationRequest;
import com.openai.commonview.translation.VideoTranslationResult;

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
        String text = "【演示模式】此处为占位译文。\n\n"
                + "接入说明：实现 VideoTranslationProvider，在 VideoTranslationInstaller 中 register 并 setActiveProviderId。\n\n"
                + "当前视频地址：\n" + url;
        callback.onSuccess(new VideoTranslationResult.Builder()
                .setTranslatedText(text)
                .setProviderId(ID)
                .build());
    }
}
