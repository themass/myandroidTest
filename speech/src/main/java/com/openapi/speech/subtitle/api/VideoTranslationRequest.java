package com.openapi.speech.subtitle.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 媒体翻译请求：将 {@link #videoUrl} 交给云端 ASR+翻译（或先抽音频再识别）。
 * 纯音频场景可把流媒体地址写入 {@link #videoUrl}，并在 extras 中附带 {@link #EXTRA_MEDIA_KIND}={@link #MEDIA_KIND_AUDIO}。
 * 具体字段如何用由 {@link VideoTranslationProvider} 决定。
 */
public final class VideoTranslationRequest {

    /** extras 键：取值 {@link #MEDIA_KIND_AUDIO} 或 {@link #MEDIA_KIND_VIDEO}，便于提供方区分处理。 */
    public static final String EXTRA_MEDIA_KIND = "mediaKind";
    public static final String MEDIA_KIND_AUDIO = "audio";
    public static final String MEDIA_KIND_VIDEO = "video";

    private final String videoUrl;
    private final Map<String, String> httpHeaders;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final Map<String, String> extras;

    private VideoTranslationRequest(Builder b) {
        this.videoUrl = b.videoUrl;
        this.httpHeaders = b.httpHeaders != null
                ? Collections.unmodifiableMap(new HashMap<>(b.httpHeaders))
                : Collections.emptyMap();
        this.sourceLanguage = b.sourceLanguage;
        this.targetLanguage = b.targetLanguage != null ? b.targetLanguage : "zh";
        this.extras = b.extras != null
                ? Collections.unmodifiableMap(new HashMap<>(b.extras))
                : Collections.emptyMap();
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    /** 源语言，null 表示由服务端自动检测。 */
    public String getSourceLanguage() {
        return sourceLanguage;
    }

    /** 目标语言，默认 zh。 */
    public String getTargetLanguage() {
        return targetLanguage;
    }

    /** 各厂商扩展参数（appId、token、模型名等）。 */
    public Map<String, String> getExtras() {
        return extras;
    }

    public static final class Builder {
        private String videoUrl;
        private Map<String, String> httpHeaders;
        private String sourceLanguage;
        private String targetLanguage = "zh";
        private Map<String, String> extras;

        public Builder setVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public Builder setHttpHeaders(Map<String, String> httpHeaders) {
            this.httpHeaders = httpHeaders;
            return this;
        }

        public Builder setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
            return this;
        }

        public Builder setTargetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
            return this;
        }

        public Builder setExtras(Map<String, String> extras) {
            this.extras = extras;
            return this;
        }

        public Builder putExtra(String key, String value) {
            if (extras == null) {
                extras = new HashMap<>();
            }
            extras.put(key, value);
            return this;
        }

        public VideoTranslationRequest build() {
            if (videoUrl == null || videoUrl.isEmpty()) {
                throw new IllegalStateException("videoUrl required");
            }
            return new VideoTranslationRequest(this);
        }
    }
}
