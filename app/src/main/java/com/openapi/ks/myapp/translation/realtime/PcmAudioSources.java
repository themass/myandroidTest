package com.openapi.ks.myapp.translation.realtime;

import android.content.Context;

import com.openai.commonutil.LogUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** 打开 PCM 音频源：优先 MediaExtractor，失败则 ExoPlayer（HLS/m3u8）。 */
public final class PcmAudioSources {

    private PcmAudioSources() {
    }

    public static PcmAudioSource open(Context context, String url, Map<String, String> headers)
            throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("Empty media URL");
        }
        String lower = url.toLowerCase(Locale.US);
        boolean likelyHls = lower.contains(".m3u8") || lower.contains("m3u8");
        if (!likelyHls) {
            try {
                return new MediaPcmAudioSource(url, headers);
            } catch (IOException extractorError) {
                LogUtil.w("MediaExtractor failed, fallback ExoPlayer: " + extractorError.getMessage());
            }
        }
        try {
            return new ExoPlayerPcmAudioSource(context, url, headers);
        } catch (Exception exoError) {
            LogUtil.e(exoError);
            String msg = exoError.getMessage();
            if (msg == null || msg.trim().isEmpty()) {
                msg = exoError.getClass().getSimpleName();
            }
            throw new IOException("Cannot decode audio from URL (HLS/progressive): " + msg, exoError);
        }
    }
}
