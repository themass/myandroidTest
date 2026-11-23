package com.openapi.speech.subtitle.realtime;

import androidx.annotation.Nullable;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Queue;

/** 从 ExoPlayer 解码链路截取 PCM，供 ASR 送流。 */
@UnstableApi
final class PcmCaptureAudioProcessor implements AudioProcessor {

    private final Queue<byte[]> captured = new ArrayDeque<>();
    private final Object lock = new Object();

    private AudioFormat inputFormat = AudioFormat.NOT_SET;
    private ByteBuffer pendingOutput = EMPTY_BUFFER;
    private boolean inputEnded;

    @Nullable
    AudioFormat getInputFormat() {
        return inputFormat == AudioFormat.NOT_SET ? null : inputFormat;
    }

    @Nullable
    byte[] pollCapturedChunk() {
        synchronized (lock) {
            return captured.poll();
        }
    }

    void clearCaptured() {
        synchronized (lock) {
            captured.clear();
        }
        pendingOutput = EMPTY_BUFFER;
        inputEnded = false;
    }

    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) {
        inputFormat = inputAudioFormat;
        inputEnded = false;
        pendingOutput = EMPTY_BUFFER;
        return inputAudioFormat;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }
        int len = inputBuffer.remaining();
        byte[] copy = new byte[len];
        inputBuffer.get(copy);
        synchronized (lock) {
            captured.add(copy);
        }
        pendingOutput = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer out = pendingOutput;
        pendingOutput = EMPTY_BUFFER;
        return out;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && pendingOutput == EMPTY_BUFFER;
    }

    @Override
    public void flush() {
        pendingOutput = EMPTY_BUFFER;
        inputEnded = false;
    }

    @Override
    public void reset() {
        clearCaptured();
        inputFormat = AudioFormat.NOT_SET;
    }
}
