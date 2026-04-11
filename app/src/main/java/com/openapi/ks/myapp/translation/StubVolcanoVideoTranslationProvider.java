package com.openapi.ks.myapp.translation;

import com.openai.commonview.translation.VideoTranslationCallback;
import com.openai.commonview.translation.VideoTranslationProvider;
import com.openai.commonview.translation.VideoTranslationRequest;

/**
 * 火山引擎占位：请在此类内对接火山「语音识别 + 翻译」等接口实现 {@link #translate}。
 */
public class StubVolcanoVideoTranslationProvider implements VideoTranslationProvider {

    public static final String ID = "volcano";

    @Override
    public String getProviderId() {
        return ID;
    }

    @Override
    public void translate(VideoTranslationRequest request, VideoTranslationCallback callback) {
        callback.onError("火山翻译：请在 StubVolcanoVideoTranslationProvider 中实现对应 HTTP/SDK 调用");
    }
}
