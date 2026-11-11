package com.openapi.speech.subtitle.realtime;

import java.io.Closeable;
import java.io.IOException;

/** 从媒体解出 16kHz mono PCM16，供火山 ASR 送流。 */
public interface PcmAudioSource extends Closeable {

    void seekToMs(long positionMs);

    /**
     * @return 写入字节数；0 暂无数据；-1 结束
     */
    int readPcm16(byte[] out, int offset, int maxBytes) throws IOException;

    /** 与播放器播放/暂停对齐（ExoPlayer 回退路径需要）。 */
    default void syncPlayback(boolean playing) {
    }

    @Override
    void close();
}
