package com.openapi.ks.myapp.translation.realtime;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.openapi.common.util.log.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 从远程媒体 URL 解出 16kHz 单声道 PCM，供流式 ASR 送包。
 * 对 HLS(m3u8) 等格式依赖系统 {@link MediaExtractor} 能力。
 */
public final class MediaPcmAudioSource implements PcmAudioSource {

    public static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_CHANNELS = 1;

    private final MediaExtractor extractor;
    private MediaCodec codec;
    private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream(32 * 1024);
    private int inputSampleRate = TARGET_SAMPLE_RATE;
    private int inputChannels = TARGET_CHANNELS;
    private boolean inputEnded;
    private boolean outputEnded;
    private boolean released;

    public MediaPcmAudioSource(String url, Map<String, String> headers) throws IOException {
        extractor = new MediaExtractor();
        if (headers != null && !headers.isEmpty()) {
            extractor.setDataSource(url, headers);
        } else {
            extractor.setDataSource(url);
        }
        int track = selectAudioTrack(extractor);
        if (track < 0) {
            extractor.release();
            throw new IOException("No audio track in media");
        }
        extractor.selectTrack(track);
        MediaFormat format = extractor.getTrackFormat(track);
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        }
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();
    }

    public void seekToMs(long positionMs) {
        if (released) {
            return;
        }
        extractor.seekTo(Math.max(0L, positionMs) * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        if (codec != null) {
            codec.flush();
        }
        pcmBuffer.reset();
        inputEnded = false;
        outputEnded = false;
    }

    /**
     * 读取 16kHz mono PCM16；返回写入字节数，0 表示暂时无数据，-1 表示结束。
     */
    public int readPcm16(byte[] out, int offset, int maxBytes) throws IOException {
        if (released || outputEnded) {
            return -1;
        }
        while (pcmBuffer.size() < maxBytes && !outputEnded) {
            if (!inputEnded) {
                feedInput();
            }
            drainOutput();
            if (inputEnded && !drainHasMore()) {
                outputEnded = true;
                break;
            }
        }
        int available = pcmBuffer.size();
        if (available <= 0) {
            return outputEnded ? -1 : 0;
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

    private boolean drainHasMore() {
        return pcmBuffer.size() > 0;
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private void feedInput() throws IOException {
        if (codec == null) {
            return;
        }
        int index = codec.dequeueInputBuffer(10_000);
        if (index < 0) {
            return;
        }
        ByteBuffer buffer = codec.getInputBuffer(index);
        if (buffer == null) {
            return;
        }
        int sampleSize = extractor.readSampleData(buffer, 0);
        if (sampleSize < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputEnded = true;
        } else {
            long pts = extractor.getSampleTime();
            codec.queueInputBuffer(index, 0, sampleSize, pts, 0);
            extractor.advance();
        }
    }

    private void drainOutput() {
        if (codec == null) {
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        for (; ; ) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            }
            if (index < 0) {
                continue;
            }
            ByteBuffer buffer = codec.getOutputBuffer(index);
            if (buffer != null && info.size > 0) {
                byte[] chunk = new byte[info.size];
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                buffer.get(chunk);
                appendResampledMono(chunk);
            }
            codec.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                outputEnded = true;
                break;
            }
        }
    }

    private void appendResampledMono(byte[] pcmChunk) {
        byte[] out = PcmAudioUtil.toTargetPcm16(pcmChunk, inputSampleRate, inputChannels);
        try {
            pcmBuffer.write(out);
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
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            try {
                codec.release();
            } catch (Exception ignored) {
            }
            codec = null;
        }
        try {
            extractor.release();
        } catch (Exception ignored) {
        }
    }
}
