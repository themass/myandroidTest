package com.openapi.speech.subtitle.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * PCM 重采样/下混的 JVM 单测：实时字幕链路里所有音频都要经过 toTargetPcm16
 * （双声道→单声道、任意采样率→16k），这里钉住长度与取值口径。
 */
public class PcmAudioUtilTest {

    private static byte[] monoSine(int frames, int amplitude) {
        short[] samples = new short[frames];
        for (int i = 0; i < frames; i++) {
            samples[i] = (short) (amplitude - i % 7);
        }
        return PcmAudioUtil.shortsToBytes(samples);
    }

    @Test
    public void 小端字节序来回转换不丢样本() {
        short[] samples = new short[]{0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE, 12345};
        byte[] bytes = PcmAudioUtil.shortsToBytes(samples);
        assertEquals(samples.length * 2, bytes.length);
        assertArrayEquals(samples, PcmAudioUtil.bytesToShorts(bytes));
    }

    @Test
    public void 双声道下混取两边平均() {
        // 交织的立体声：(L,R) = (100,200),(300,400) → 平均 (150,350)
        byte[] stereo = PcmAudioUtil.shortsToBytes(new short[]{100, 200, 300, 400});
        byte[] mono = PcmAudioUtil.toTargetPcm16(stereo, 16000, 2);
        assertArrayEquals(new short[]{150, 350}, PcmAudioUtil.bytesToShorts(mono));
    }

    @Test
    public void 四十八秒采样率降到十六千长度按比例缩短() {
        byte[] input = monoSine(4800, 10000);
        byte[] output = PcmAudioUtil.toTargetPcm16(input, 48000, 1);
        // 线性插值重采样：输出帧数 = ceil(4800 / 3) = 1600
        assertEquals(1600 * 2, output.length);
    }

    @Test
    public void 采样率声道都对时逐样本透传() {
        byte[] input = monoSine(800, 123);
        byte[] output = PcmAudioUtil.toTargetPcm16(input, 16000, 1);
        assertArrayEquals(input, output);
    }

    @Test
    public void 单声道请求对单声道输入是恒等() {
        short[] samples = new short[]{5, 6, 7};
        assertArrayEquals(samples, PcmAudioUtil.downmixToMono(samples, 1));
    }
}
