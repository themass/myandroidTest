package com.openai.commonview.translation;

/**
 * 翻译结果：展示用中文文案 + 可选原始识别文本。
 */
public final class VideoTranslationResult {

    private final String translatedText;
    private final String rawAsrText;
    private final String providerId;

    private VideoTranslationResult(Builder b) {
        this.translatedText = b.translatedText;
        this.rawAsrText = b.rawAsrText;
        this.providerId = b.providerId;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public String getRawAsrText() {
        return rawAsrText;
    }

    public String getProviderId() {
        return providerId;
    }

    public static final class Builder {
        private String translatedText;
        private String rawAsrText;
        private String providerId;

        public Builder setTranslatedText(String translatedText) {
            this.translatedText = translatedText;
            return this;
        }

        public Builder setRawAsrText(String rawAsrText) {
            this.rawAsrText = rawAsrText;
            return this;
        }

        public Builder setProviderId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public VideoTranslationResult build() {
            return new VideoTranslationResult(this);
        }
    }
}
