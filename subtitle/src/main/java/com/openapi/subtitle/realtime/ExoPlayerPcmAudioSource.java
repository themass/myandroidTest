package com.openapi.subtitle.realtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.openapi.common.util.log.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HLS(m3u8) 等 {@link android.media.MediaExtractor} 无法直接打开的流，用 Media3 ExoPlayer 解音频。
 * ExoPlayer 必须在主线程 Looper 上创建/控制（华为等机型在后台 HandlerThread 上易 prepare 超时）。
 */
@OptIn(markerClass = UnstableApi.class)
public final class ExoPlayerPcmAudioSource implements PcmAudioSource {

    private static final int PREPARE_TIMEOUT_SEC = 45;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PcmCaptureAudioProcessor capture = new PcmCaptureAudioProcessor();
    private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream(32 * 1024);
    private ExoPlayer player;
    private boolean released;
    private boolean streamEnded;

    public ExoPlayerPcmAudioSource(Context context, String url, Map<String, String> headers) throws IOException {
        Context app = context.getApplicationContext();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<IOException> initError = new AtomicReference<>();
        AtomicReference<String> lastState = new AtomicReference<>("init");

        mainHandler.post(() -> {
            try {
                DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(app) {
                    @Override
                    protected AudioSink buildAudioSink(
                            Context context,
                            boolean enableFloatOutput,
                            boolean enableAudioOutputPlaybackParams) {
                        return new DefaultAudioSink.Builder(context)
                                .setEnableFloatOutput(enableFloatOutput)
                                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                                .setAudioProcessors(new AudioProcessor[]{capture})
                                .build();
                    }
                };
                player = new ExoPlayer.Builder(app, renderersFactory).build();

                DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(app)
                        .setDataSourceFactory(PcmHttpDataSourceFactory.create(headers));
                player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(url)));

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        lastState.set(stateName(playbackState));
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                            ready.countDown();
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        initError.set(new IOException("ExoPlayer error "
                                + error.errorCode + " (" + error.getErrorCodeName() + "): "
                                + error.getMessage()));
                        ready.countDown();
                    }
                });
                LogUtil.i("ExoPlayerPcmAudioSource prepare url=" + url);
                player.prepare();
                player.setVolume(0f);
                player.setPlayWhenReady(true);
            } catch (Exception e) {
                initError.set(new IOException("ExoPlayer init failed: " + e.getMessage(), e));
                ready.countDown();
            }
        });

        try {
            if (!ready.await(PREPARE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                throw new IOException("ExoPlayer prepare timeout (last state="
                        + lastState.get() + ", waited " + PREPARE_TIMEOUT_SEC + "s)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ExoPlayer interrupted", e);
        }
        IOException error = initError.get();
        if (error != null) {
            releaseOnMain();
            throw error;
        }
        if (!isReadyOnMain()) {
            releaseOnMain();
            throw new IOException("ExoPlayer not ready (last state=" + lastState.get() + ")");
        }
    }

    private static String stateName(int state) {
        switch (state) {
            case Player.STATE_IDLE:
                return "IDLE";
            case Player.STATE_BUFFERING:
                return "BUFFERING";
            case Player.STATE_READY:
                return "READY";
            case Player.STATE_ENDED:
                return "ENDED";
            default:
                return String.valueOf(state);
        }
    }

    private boolean isReadyOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return player != null && player.getPlaybackState() == Player.STATE_READY;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        mainHandler.post(() -> {
            ready.set(player != null && player.getPlaybackState() == Player.STATE_READY);
            latch.countDown();
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Boolean.TRUE.equals(ready.get());
    }

    @Override
    public void seekToMs(long positionMs) {
        if (released) {
            return;
        }
        capture.clearCaptured();
        pcmBuffer.reset();
        streamEnded = false;
        mainHandler.post(() -> {
            if (player != null) {
                player.seekTo(Math.max(0L, positionMs));
            }
        });
    }

    @Override
    public void syncPlayback(boolean playing) {
        if (released) {
            return;
        }
        mainHandler.post(() -> {
            if (player != null) {
                player.setPlayWhenReady(playing);
            }
        });
    }

    @Override
    public int readPcm16(byte[] out, int offset, int maxBytes) throws IOException {
        if (released || streamEnded) {
            return -1;
        }
        while (pcmBuffer.size() < maxBytes) {
            byte[] chunk = capture.pollCapturedChunk();
            if (chunk == null) {
                if (isEndedOnMain()) {
                    streamEnded = true;
                    break;
                }
                break;
            }
            appendChunk(chunk);
        }
        int available = pcmBuffer.size();
        if (available <= 0) {
            return streamEnded ? -1 : 0;
        }
        int toRead = Math.min(maxBytes, available);
        byte[] all = pcmBuffer.toByteArray();
        System.arraycopy(all, 0, out, offset, toRead);
        pcmBuffer.reset();
        if (toRead < all.length) {
            pcmBuffer.write(all, toRead, all.length - toRead);
        }
        return toRead;
    }

    private boolean isEndedOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return player != null && player.getPlaybackState() == Player.STATE_ENDED;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> ended = new AtomicReference<>(false);
        mainHandler.post(() -> {
            ended.set(player != null && player.getPlaybackState() == Player.STATE_ENDED);
            latch.countDown();
        });
        try {
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Boolean.TRUE.equals(ended.get());
    }

    private void appendChunk(byte[] chunk) {
        AudioProcessor.AudioFormat format = capture.getInputFormat();
        if (format == null || format.sampleRate <= 0) {
            return;
        }
        if (format.encoding == C.ENCODING_PCM_FLOAT) {
            LogUtil.w("ExoPlayerPcmAudioSource: float PCM not converted");
            return;
        }
        if (format.encoding != C.ENCODING_PCM_16BIT) {
            LogUtil.w("ExoPlayerPcmAudioSource: unsupported encoding " + format.encoding);
            return;
        }
        byte[] converted = PcmAudioUtil.toTargetPcm16(chunk, format.sampleRate, format.channelCount);
        try {
            pcmBuffer.write(converted);
        } catch (IOException e) {
            LogUtil.e(e);
        }
    }

    @Override
    public void close() {
        if (released) {
            return;
        }
        released = true;
        releaseOnMain();
        capture.reset();
    }

    private void releaseOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releasePlayerInternal();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            releasePlayerInternal();
            latch.countDown();
        });
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void releasePlayerInternal() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception e) {
                LogUtil.e(e);
            }
            player = null;
        }
    }
}
