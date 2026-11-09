package com.openapi.ks.myapp.translation.realtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PcmAudioUtil {

    private PcmAudioUtil() {
    }

    static short[] bytesToShorts(byte[] data) {
        short[] out = new short[data.length / 2];
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    static byte[] shortsToBytes(short[] data) {
        byte[] out = new byte[data.length * 2];
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(data);
        return out;
    }

    static short[] downmixToMono(short[] samples, int channels) {
        if (channels <= 1) {
            return samples;
        }
        int frames = samples.length / channels;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += samples[i * channels + c];
            }
            mono[i] = (short) (sum / channels);
        }
        return mono;
    }

    static short[] resample(short[] input, int fromRate, int toRate) {
        if (fromRate == toRate || input.length == 0) {
            return input;
        }
        double ratio = (double) fromRate / (double) toRate;
        int outLen = (int) Math.ceil(input.length / ratio);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double src = i * ratio;
            int idx = (int) src;
            if (idx >= input.length - 1) {
                out[i] = input[input.length - 1];
            } else {
                double frac = src - idx;
                out[i] = (short) ((1.0 - frac) * input[idx] + frac * input[idx + 1]);
            }
        }
        return out;
    }

    static byte[] toTargetPcm16(byte[] pcmChunk, int inputSampleRate, int inputChannels) {
        short[] input = bytesToShorts(pcmChunk);
        short[] mono = downmixToMono(input, inputChannels);
        short[] resampled = resample(mono, inputSampleRate, MediaPcmAudioSource.TARGET_SAMPLE_RATE);
        return shortsToBytes(resampled);
    }
}
