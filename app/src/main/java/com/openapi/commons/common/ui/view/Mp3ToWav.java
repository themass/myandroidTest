package com.openapi.commons.common.ui.view;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.openapi.commons.common.util.LogUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Mp3ToWav {
    public static void convertMP3ToPCM(String mp3FilePath, String pcmFilePath) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        FileOutputStream outputStream = null;

        try {
            extractor.setDataSource(mp3FilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No track found in " + mp3FilePath);
            }

            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            outputStream = new FileOutputStream(pcmFilePath);
            decodeToPCM(extractor, codec, outputStream);
        } catch (IOException e) {
            LogUtil.e(e);
        } finally {
            if (codec != null) {
                codec.stop();
                codec.release();
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    LogUtil.e(e);
                }
            }
            extractor.release();
        }
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static void decodeToPCM(MediaExtractor extractor, MediaCodec codec, FileOutputStream outputStream) throws IOException {
        final long timeoutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawOutputEOS = false;

        while (!sawOutputEOS) {
            int inputBufIndex = codec.dequeueInputBuffer(timeoutUs);
            if (inputBufIndex >= 0) {
                ByteBuffer dstBuf = codec.getInputBuffer(inputBufIndex);
                int sampleSize = extractor.readSampleData(dstBuf, 0);
                long presentationTimeUs = 0;
                if (sampleSize < 0) {
                    sawOutputEOS = true;
                    sampleSize = 0;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }
                codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, sawOutputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                if (!sawOutputEOS) {
                    extractor.advance();
                }
            }

            int outputBufIndex = codec.dequeueOutputBuffer(info, timeoutUs);
            if (outputBufIndex >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(outputBufIndex);
                final byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();
                if (chunk.length > 0) {
                    outputStream.write(chunk);
                }
                codec.releaseOutputBuffer(outputBufIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            }
        }
    }

}
