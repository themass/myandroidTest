package com.openapi.speech.subtitle.realtime;

import com.openapi.speech.asr.VolcengineAsrConfig;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.openapi.speech.subtitle.api.RealtimeSubtitleCallbacks;
import com.openapi.speech.subtitle.api.RealtimeSubtitleSession;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频播放页 {@link RealtimeSubtitleSession}：从当前 URL 解音频并走火山流式 ASR。
 */
public final class VideoRealtimeSubtitleSession implements RealtimeSubtitleSession {

    private final Context appContext;
    private final ViewGroup overlayParent;
    private final MediaUrlProvider urlProvider;
    private final HeadersProvider headersProvider;
    private final PlaybackProbe playbackProbe;

    private RealtimeSubtitleEngine engine;
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface MediaUrlProvider {
        String getUrl();
    }

    public interface HeadersProvider {
        Map<String, String> getHeaders();
    }

    public interface PlaybackProbe {
        boolean isPlaying();

        long positionMs();
    }

    public VideoRealtimeSubtitleSession(Context context, ViewGroup overlayParent,
                                        MediaUrlProvider urlProvider,
                                        HeadersProvider headersProvider,
                                        PlaybackProbe playbackProbe) {
        this.appContext = context.getApplicationContext();
        this.overlayParent = overlayParent;
        this.urlProvider = urlProvider;
        this.headersProvider = headersProvider;
        this.playbackProbe = playbackProbe;
    }

    @NonNull
    @Override
    public ViewGroup getOverlayParent() {
        return overlayParent;
    }

    @Override
    public boolean isConfigured() {
        return VolcengineAsrConfig.load(appContext).isConfigured();
    }

    @Override
    public void startStreaming(long playbackPositionMs, @NonNull RealtimeSubtitleCallbacks callbacks) {
        stopStreaming();
        streaming.set(true);
        RealtimeSubtitleEngine.PlaybackProbe probe = playbackProbe == null ? null : new RealtimeSubtitleEngine.PlaybackProbe() {
            @Override
            public boolean isPlaying() {
                return playbackProbe.isPlaying();
            }

            @Override
            public long positionMs() {
                return playbackProbe.positionMs();
            }
        };
        engine = new RealtimeSubtitleEngine(
                appContext,
                urlProvider != null ? urlProvider.getUrl() : "",
                headersProvider != null ? headersProvider.getHeaders() : null,
                probe);
        RealtimeSubtitleCallbacks wrapped = new RealtimeSubtitleCallbacks() {
            @Override
            public void onLoading() {
                main.post(callbacks::onLoading);
            }

            @Override
            public void onPartialText(@NonNull String text) {
                main.post(() -> callbacks.onPartialText(text));
            }

            @Override
            public void onError(@NonNull String message) {
                main.post(() -> {
                    streaming.set(false);
                    callbacks.onError(message);
                });
            }

            @Override
            public void onStopped() {
                main.post(() -> {
                    streaming.set(false);
                    callbacks.onStopped();
                });
            }
        };
        engine.start(playbackPositionMs, wrapped);
    }

    @Override
    public void stopStreaming() {
        streaming.set(false);
        if (engine != null) {
            engine.stop();
            engine = null;
        }
    }

    @Override
    public boolean isStreaming() {
        return streaming.get() || (engine != null && engine.isRunning());
    }
}
