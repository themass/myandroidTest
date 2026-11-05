package com.openapi.core.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Mp3ToWav {
    public static boolean convertMP3ToPCM(String audioFilePath, String pcmFilePath) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        FileOutputStream outputStream = null;

        try {
            File input = new File(audioFilePath);
            if (!input.exists() || input.length() == 0) {
                LogUtil.w("convertMP3ToPCM: input missing or empty: " + audioFilePath);
                return false;
            }
            extractor.setDataSource(audioFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                LogUtil.w("convertMP3ToPCM: no audio track in " + audioFilePath);
                return false;
            }

            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            File pcmFile = new File(pcmFilePath);
            File parent = pcmFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LogUtil.w("convertMP3ToPCM: cannot create parent dir " + parent.getAbsolutePath());
                return false;
            }
            outputStream = new FileOutputStream(pcmFile);
            decodeToPCM(extractor, codec, outputStream);
            outputStream.flush();
            return pcmFile.exists() && pcmFile.length() > 0;
        } catch (Exception e) {
            LogUtil.e("convertMP3ToPCM failed: " + audioFilePath, e);
            return false;
        } finally {
            try {
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
            } catch (Exception e) {
                LogUtil.e(e);
            }
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
