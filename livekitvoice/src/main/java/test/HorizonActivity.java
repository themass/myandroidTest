package test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yalantis.audio.lib.AudioUtil;
import com.yalantis.waves.util.Horizon;

import io.livekit.android.example.voiceassistant.R;

public class HorizonActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_RECORD_AUDIO = 1;

    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = 1;
    private static final int RECORDER_ENCODING_BIT = 16;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int MAX_DECIBELS = 120;

    private AudioRecord audioRecord;
    private Horizon mHorizon;
    private GLSurfaceView glSurfaceView;

    private Thread recordingThread;
    private byte[] buffer;

    /**
     * this listener helps us to synchronise real time
     * and actual drawing
     */
    private AudioRecord.OnRecordPositionUpdateListener recordPositionUpdateListener = new AudioRecord.OnRecordPositionUpdateListener() {
        @Override
        public void onMarkerReached(AudioRecord recorder) {
            //empty for now
        }

        @Override
        public void onPeriodicNotification(AudioRecord recorder) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING
                    && audioRecord.read(buffer, 0, buffer.length) != -1) {
                mHorizon.updateView(buffer);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.horizony_demo);
        glSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface);
        mHorizon = new Horizon(glSurfaceView, getResources().getColor(R.color.black),
                RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_ENCODING_BIT);
        mHorizon.setMaxVolumeDb(MAX_DECIBELS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissionsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (audioRecord != null) {
            audioRecord.release();
        }
        AudioUtil.disposeProcessor();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_RECORD_AUDIO:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkPermissionsAndStart();
                } else {
                    finish();
                }
        }

    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSION_RECORD_AUDIO);
        } else {
            initRecorder();
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                startRecording();
            }
        }
    }

    private void initRecorder() {
        final int bufferSize = 2 * AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
        AudioUtil.initProcessor(RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_ENCODING_BIT);

        recordingThread = new Thread("recorder") {
            @Override
            public void run() {
                super.run();
                buffer = new byte[bufferSize];
                Looper.prepare();
                audioRecord.setRecordPositionUpdateListener(recordPositionUpdateListener, new Handler(Looper.myLooper()));
                int bytePerSample = RECORDER_ENCODING_BIT / 8;
                float samplesToDraw = bufferSize / bytePerSample;
                audioRecord.setPositionNotificationPeriod((int) samplesToDraw);
                //We need to read first chunk to motivate recordPositionUpdateListener.
                //Mostly, for lower versions - https://code.google.com/p/android/issues/detail?id=53996
                audioRecord.read(buffer, 0, bufferSize);
                Looper.loop();
            }
        };
    }

    private void startRecording() {
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        recordingThread.start();
    }
}
