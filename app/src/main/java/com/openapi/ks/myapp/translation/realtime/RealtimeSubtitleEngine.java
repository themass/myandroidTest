package com.openapi.ks.myapp.translation.realtime;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.openapi.core.util.LogUtil;
import com.openapi.core.util.StringUtils;
import com.openapi.core.view.translation.RealtimeSubtitleCallbacks;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 后台线程：解音频 → 火山 WebSocket 流式识别。
 */
public final class RealtimeSubtitleEngine {

    private static final int TARGET_SAMPLE_RATE = MediaPcmAudioSource.TARGET_SAMPLE_RATE;
    private static final int CHUNK_MS = 100;
    private static final int CHUNK_BYTES = TARGET_SAMPLE_RATE * 2 * CHUNK_MS / 1000;

    private final Context appContext;
    private final String mediaUrl;
    private final Map<String, String> headers;
    private final PlaybackProbe playbackProbe;

    private HandlerThread workerThread;
    private Handler worker;
    private VolcengineStreamingAsrClient asrClient;
    private PcmAudioSource audioSource;
    private RealtimeSubtitleCallbacks callbacks;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public interface PlaybackProbe {
        boolean isPlaying();

        long positionMs();
    }

    public RealtimeSubtitleEngine(Context context, String mediaUrl, Map<String, String> headers,
                                  PlaybackProbe playbackProbe) {
        this.appContext = context.getApplicationContext();
        this.mediaUrl = mediaUrl;
        this.headers = headers;
        this.playbackProbe = playbackProbe;
    }

    public void start(long startPositionMs, RealtimeSubtitleCallbacks callbacks) {
        stop();
        if (!StringUtils.hasText(mediaUrl)) {
            callbacks.onError("Play URL is empty");
            return;
        }
        VolcengineAsrConfig config = VolcengineAsrConfig.load(appContext);
        if (!config.isConfigured()) {
            callbacks.onError("Volcano ASR credentials not configured");
            return;
        }

        this.callbacks = callbacks;
        stopRequested.set(false);
        running.set(true);
        callbacks.onLoading();

        workerThread = new HandlerThread("realtime-subtitle");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        worker.post(() -> runSession(config, startPositionMs));
    }

    public void stop() {
        stopRequested.set(true);
        running.set(false);
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
        }
        if (asrClient != null) {
            asrClient.cancel();
            asrClient = null;
        }
        closeAudio();
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
            worker = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void runSession(VolcengineAsrConfig config, long startPositionMs) {
        try {
            audioSource = PcmAudioSources.open(appContext, mediaUrl, headers);
            audioSource.seekToMs(startPositionMs);

            asrClient = new VolcengineStreamingAsrClient(config);
            CountDownLatch settled = new CountDownLatch(1);
            AtomicBoolean asrReady = new AtomicBoolean(false);
            AtomicReference<String> connectError = new AtomicReference<>();
            asrClient.connect(new VolcengineStreamingAsrClient.Listener() {
                @Override
                public void onReady() {
                    asrReady.set(true);
                    settled.countDown();
                }

                @Override
                public void onPartialText(String text) {
                    if (callbacks != null && running.get()) {
                        callbacks.onPartialText(text);
                    }
                }

                @Override
                public void onFailure(String message) {
                    connectError.compareAndSet(null, message != null ? message : "ASR failed");
                    settled.countDown();
                    stopRequested.set(true);
                    if (asrReady.get() && callbacks != null) {
                        callbacks.onError(connectError.get());
                    }
                }

                @Override
                public void onClosed() {
                    running.set(false);
                    if (callbacks != null) {
                        callbacks.onStopped();
                    }
                }
            });

            if (!settled.await(25, TimeUnit.SECONDS)) {
                throw new IllegalStateException("ASR 连接超时，请检查网络与火山密钥");
            }
            String initError = connectError.get();
            if (initError != null) {
                throw new IllegalStateException(initError);
            }

            byte[] chunk = new byte[CHUNK_BYTES];
            while (!stopRequested.get()) {
                boolean playing = playbackProbe == null || playbackProbe.isPlaying();
                audioSource.syncPlayback(playing);
                if (!playing) {
                    Thread.sleep(80);
                    continue;
                }
                int read = audioSource.readPcm16(chunk, 0, chunk.length);
                if (read < 0) {
                    asrClient.sendPcm(new byte[0], 0, 0, true);
                    break;
                }
                if (read == 0) {
                    Thread.sleep(20);
                    continue;
                }
                asrClient.sendPcm(chunk, 0, read, false);
                Thread.sleep(CHUNK_MS);
            }
        } catch (Exception e) {
            LogUtil.e(e);
            if (callbacks != null) {
                callbacks.onError(e.getMessage() != null ? e.getMessage() : "Streaming subtitle failed");
            }
        } finally {
            running.set(false);
            if (asrClient != null) {
                asrClient.finish();
                asrClient = null;
            }
            closeAudio();
        }
    }

    private void closeAudio() {
        if (audioSource != null) {
            try {
                audioSource.close();
            } catch (Exception ignored) {
            }
            audioSource = null;
        }
    }
}
