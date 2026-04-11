package com.openai.commonview.translation;

/**
 * 翻译异步回调；可在任意线程调用，{@link VideoTranslationRegistry} 会转发到主线程。
 */
public interface VideoTranslationCallback {

    void onSuccess(VideoTranslationResult result);

    void onError(String message);
}
