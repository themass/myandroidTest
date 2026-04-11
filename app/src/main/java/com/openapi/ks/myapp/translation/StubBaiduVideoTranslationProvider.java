package com.openapi.ks.myapp.translation;

import com.openai.commonview.translation.VideoTranslationCallback;
import com.openai.commonview.translation.VideoTranslationProvider;
import com.openai.commonview.translation.VideoTranslationRequest;

/**
 * 百度占位：请在此类内使用「短语音识别 + 机器翻译」等开放 API 实现 {@link #translate}。
 */
public class StubBaiduVideoTranslationProvider implements VideoTranslationProvider {

    public static final String ID = "baidu";

    @Override
    public String getProviderId() {
        return ID;
    }

    @Override
    public void translate(VideoTranslationRequest request, VideoTranslationCallback callback) {
        callback.onError("百度翻译：请在 StubBaiduVideoTranslationProvider 中实现（如语音极速版 ASR + 通用翻译 API）");
    }
}
