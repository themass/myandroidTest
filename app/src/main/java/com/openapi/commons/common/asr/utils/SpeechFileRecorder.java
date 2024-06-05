package com.openapi.commons.common.asr.utils;

import android.util.Log;

import com.bytedance.speech.speechengine.SpeechEngine;

import java.io.InputStream;

public class SpeechFileRecorder {

    private static final int SAMPLE_RATE = 8000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final float BUFFER_SIZE_IN_SECONDS = 0.08f;

    private Thread mWorker = null;

    private SpeechEngine mSpeechEngine = null;
    private long mSpeechEngineHandler = -1;

    private String mPath;
    private String mFilename;

    public int GetStreamSampleRate() {
        return SAMPLE_RATE;
    }

    public void SetSpeechEngine(SpeechEngine speechEngine, long speechEngineHandler) {
        mSpeechEngine = speechEngine;
        mSpeechEngineHandler = speechEngineHandler;
    }

    public boolean Start(String path, String filename) {
        mPath = path;
        mFilename = filename;
        if (null != mWorker) {
            if (mWorker.isAlive()) {
                Log.w(SpeechDemoDefines.TAG, "Already start!");
                return true;
            }
            mWorker = null;
        }

        mWorker = new RecorderThread();
        mWorker.start();
        Log.i(SpeechDemoDefines.TAG, "Stream Recorder Started.");
        return true;
    }

    public void Stop() {
        if (null == mWorker) {
            Log.w(SpeechDemoDefines.TAG, "Not start yet!");
            return;
        }
        mWorker.interrupt();

        try {
            mWorker.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }

        mWorker = null;
        Log.i(SpeechDemoDefines.TAG, "Stream Recorder Stopped.");
    }

    private final class RecorderThread extends Thread {
        @Override
        public void run() {
            InputStream in = SpeechFileUtils.OpenInputFile(mPath, mFilename);
            int bufferSize = Math.round(SAMPLE_RATE * BUFFER_SIZE_IN_SECONDS * BYTES_PER_SAMPLE);
            byte[] buffer = new byte[bufferSize];
            int nread;
            int total = 0;

            while (!interrupted()) {
                nread = SpeechFileUtils.ReadData(in, buffer, bufferSize);
                if (nread > 0) {
                    total += nread;
                    int ret = mSpeechEngine.feedAudio(mSpeechEngineHandler, buffer, nread);
                    if (ret != 0) {
                        Log.e(SpeechDemoDefines.TAG, "Feed audio failed.");
                    }
                } else if (nread < 0) {
                    Log.e(SpeechDemoDefines.TAG, "Recorder error.");
                    break;
                }
            }
            Log.i(SpeechDemoDefines.TAG, "total: " + total);
            SpeechFileUtils.CloseInputFile(in);
        }
    }

}
