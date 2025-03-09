//package test;
//
//import android.annotation.SuppressLint;
//import android.graphics.Color;
//import android.graphics.RadialGradient;
//import android.graphics.Shader;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Message;
//import android.util.Log;
//import android.view.Gravity;
//import android.view.View;
//import android.widget.Button;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.aghajari.waveanimation.AXWaveView;
//import com.aghajari.waveanimation.AXWeavingState;
//
//import java.io.File;
//import java.io.IOException;
//
//import io.livekit.android.example.voiceassistant.R;
//import jaygoo.widget.wlv.WaveLineView;
//import me.xfans.lib.voicewaveview.LineType;
//import me.xfans.lib.voicewaveview.VoiceWaveView;
//import me.xfans.lib.voicewaveview.WaveMode;
//
//public class WaveTest2 extends AppCompatActivity implements Runnable {
//
//    VoiceWaveView voiceWaveView0;
//    VoiceWaveView voiceWaveView1;
//    VoiceWaveView voiceWaveView2;
//    VoiceWaveView voiceWaveView3;
//    VoiceWaveView voiceWaveView4;
//    VoiceWaveView voiceWaveView5;
//     AXWaveView waveView;
//
//    private WaveLineView waveLineView;
//
//    private MediaRecorder mMediaRecorder;
//    private boolean isAlive = true;
//    private VoiceLineView voiceLineView;
//    private Button stopVoice;
//    private Handler handler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            if (mMediaRecorder == null) return;
//            double ratio = (double) mMediaRecorder.getMaxAmplitude() / 100;   //mMediaRecorder.getMaxAmplitude()获取在前一次调用此方法之后录音中出现的最大振幅。
//            double db = 0;// 分贝
//            //默认的最大音量是100,可以修改，但其实默认的，在测试过程中就有不错的表现
//            //你可以传自定义的数字进去，但需要在一定的范围内，比如0-200，就需要在xml文件中配置maxVolume
//            //同时，也可以配置灵敏度sensibility
//
//            if (ratio > 1)
//                db = 20 * Math.log10(ratio);
//            //只要有一个线程，不断调用这个方法，就可以使波形变化
//            //主要，这个方法必须在ui线程中调用
////            Log.i("=======", db+"——————"+ratio);
//            voiceLineView.setVolume((int) (db));
//            waveLineView.setVolume((int) (db));//这里穿进去的就是分贝
//            int i = (int)(ratio*85);
//            Log.i("=======", i+"——————"+ratio+"======"+db);
//            waveView.setAmplitude(i);
//        }
//    };
//
//    @SuppressLint("MissingInflatedId")
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.test_wave);
//        voiceLineView = (VoiceLineView) findViewById(R.id.voicLine);
//        waveLineView = (WaveLineView) findViewById(R.id.waveLineView);
//
////        voiceLineView.startWave();
//        if (mMediaRecorder == null)
//            mMediaRecorder = new MediaRecorder();
//
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//        File file = new File(this.getFilesDir().getAbsolutePath(), "hello.log");
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        stopVoice = (Button) findViewById(R.id.stopVoice);
//        mMediaRecorder.setOutputFile(file.getAbsolutePath());
//        mMediaRecorder.setMaxDuration(1000 * 60 * 10);
//        try {
//            mMediaRecorder.prepare();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        mMediaRecorder.start();
//
//        final Thread thread = new Thread(this);
//        thread.start();
//
//        stopVoice.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(voiceLineView.isShow()){
//                    voiceLineView.stopWave();
//                    waveLineView.stopAnim();
//                    stopVoice.setText("开始");
//                }else {
//                    voiceLineView.startWave();
//                    waveLineView.startAnim();
//                    stopVoice.setText("停止");
//                }
//            }
//        });
//        try {
//            init();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//    private void init() throws Exception {
//        voiceWaveView0 = findViewById(R.id.voiceWaveView0);
//        voiceWaveView1 = findViewById(R.id.voiceWaveView1);
//        voiceWaveView2 = findViewById(R.id.voiceWaveView2);
//        voiceWaveView3 = findViewById(R.id.voiceWaveView3);
//        voiceWaveView4 = findViewById(R.id.voiceWaveView4);
//        voiceWaveView5 = findViewById(R.id.voiceWaveView5);
//        voiceWaveView0.setDuration(150);
//        voiceWaveView0.addHeader(2);
//        voiceWaveView0.addHeader(2);
//        voiceWaveView0.addHeader(2);
//        voiceWaveView0.addHeader(2);
//        voiceWaveView0.addHeader(4);
//        voiceWaveView0.addHeader(14);
//        voiceWaveView0.addBody(27);
//        voiceWaveView0.addBody(17);
//        voiceWaveView0.addBody(38);
//        voiceWaveView0.addBody(91);
//        voiceWaveView0.addBody(38);
//        voiceWaveView0.addBody(24);
//        voiceWaveView0.addBody(8);
//        voiceWaveView0.addBody(60);
//        voiceWaveView0.addBody(38);
//        voiceWaveView0.addBody(14);
//        voiceWaveView0.addBody(8);
//        voiceWaveView0.addFooter(4);
//        voiceWaveView0.addFooter(2);
//        voiceWaveView0.addFooter(2);
//        voiceWaveView0.addFooter(2);
//        voiceWaveView0.addFooter(2);
//        voiceWaveView0.start();
//        if (voiceWaveView1 != null) {
//            voiceWaveView1.setShowGravity(Gravity.RIGHT | Gravity.TOP);
//            voiceWaveView1.setWaveMode(WaveMode.UP_DOWN);
//            voiceWaveView1.setLineWidth(30f);
//            voiceWaveView1.setLineSpace(15f);
//            voiceWaveView1.setDuration(500);
//            voiceWaveView1.setLineColor(Color.parseColor("#F56B00"));
//            voiceWaveView1.addHeader(4);
//            voiceWaveView1.addHeader(14);
//            voiceWaveView1.addBody(27);
//            voiceWaveView1.addBody(17);
//            voiceWaveView1.addBody(38);
//            voiceWaveView1.addBody(91);
//            voiceWaveView1.addBody(38);
//            voiceWaveView1.addBody(24);
//            voiceWaveView1.addBody(8);
//            voiceWaveView1.addBody(60);
//            voiceWaveView1.addBody(38);
//            voiceWaveView1.addBody(14);
//            voiceWaveView1.addBody(8);
//            voiceWaveView1.addFooter(4);
//            voiceWaveView1.addFooter(2);
//            voiceWaveView1.start();
//        }
//
//        if (voiceWaveView2 != null) {
//            voiceWaveView2.setLineType(LineType.LINE_GRAPH);
//            voiceWaveView2.setLineWidth(2f);
//            voiceWaveView2.setLineSpace(50f);
//            voiceWaveView2.setDuration(200);
//            voiceWaveView2.addBody(14);
//            voiceWaveView2.addBody(27);
//            voiceWaveView2.addBody(17);
//            voiceWaveView2.addBody(38);
//            voiceWaveView2.addBody(91);
//            voiceWaveView2.addBody(38);
//            voiceWaveView2.addBody(24);
//            voiceWaveView2.addBody(8);
//            voiceWaveView2.addBody(60);
//            voiceWaveView2.addBody(38);
//            voiceWaveView2.addBody(14);
//            voiceWaveView2.addBody(8);
//            voiceWaveView2.addBody(4);
//            voiceWaveView2.addBody(2);
//            voiceWaveView2.addBody(2);
//            voiceWaveView2.start();
//        }
//
//        if (voiceWaveView3 != null) {
//            voiceWaveView3.addHeader(14);
//            voiceWaveView3.addBody(27);
//            voiceWaveView3.addBody(17);
//            voiceWaveView3.addBody(38);
//            voiceWaveView3.addBody(91);
//            voiceWaveView3.addBody(38);
//            voiceWaveView3.addBody(24);
//            voiceWaveView3.addBody(8);
//            voiceWaveView3.addBody(60);
//            voiceWaveView3.addBody(38);
//            voiceWaveView3.addBody(14);
//            voiceWaveView3.addBody(8);
//            voiceWaveView3.addFooter(4);
//            voiceWaveView3.addFooter(2);
//            voiceWaveView3.start();
//        }
//
//        if (voiceWaveView4 != null) {
//            voiceWaveView4.setLineColor(Color.GREEN);
//            voiceWaveView4.addHeader(14);
//            voiceWaveView4.addBody(27);
//            voiceWaveView4.addBody(17);
//            voiceWaveView4.addBody(38);
//            voiceWaveView4.addBody(91);
//            voiceWaveView4.addBody(38);
//            voiceWaveView4.addBody(24);
//            voiceWaveView4.addBody(8);
//            voiceWaveView4.addBody(60);
//            voiceWaveView4.addBody(38);
//            voiceWaveView4.addBody(14);
//            voiceWaveView4.addBody(8);
//            voiceWaveView4.addFooter(4);
//            voiceWaveView4.addFooter(2);
//            voiceWaveView4.start();
//        }
//
//        if (voiceWaveView5 != null) {
//            voiceWaveView5.setLineColor(Color.YELLOW);
//            voiceWaveView5.addHeader(3);
//            voiceWaveView5.addHeader(3);
//            voiceWaveView5.addHeader(3);
//            voiceWaveView5.addHeader(3);
//            voiceWaveView5.addHeader(3);
//            voiceWaveView5.addHeader(4);
//            voiceWaveView5.addHeader(14);
//            voiceWaveView5.addBody(27);
//            voiceWaveView5.addBody(17);
//            voiceWaveView5.addBody(38);
//            voiceWaveView5.addBody(91);
//            voiceWaveView5.addBody(38);
//            voiceWaveView5.addBody(24);
//            voiceWaveView5.addBody(8);
//            voiceWaveView5.addBody(60);
//            voiceWaveView5.addBody(38);
//            voiceWaveView5.addBody(14);
//            voiceWaveView5.addBody(8);
//            voiceWaveView5.addFooter(4);
//            voiceWaveView5.addFooter(3);
//            voiceWaveView5.addFooter(3);
//            voiceWaveView5.addFooter(3);
//            voiceWaveView5.addFooter(3);
//            voiceWaveView5.start();
//        }
//        waveView = findViewById(R.id.wave);
//        waveView.addState(1, AXWeavingState.create(1,
//                new RadialGradient(200,200,200, new int[]{0xff2BCEFF,0xff0976E3}
//                        ,null, Shader.TileMode.CLAMP)));
//
//        waveView.addState(2, new AXWeavingState(2){
//
//            @Override
//            protected void updateTargets() {
//                targetX = 0.2f + 0.1f * random.nextInt(100) / 100f;
//                targetY = 0.7f + 0.1f * random.nextInt(100) / 100f;
//            }
//
//            @Override
//            public Shader createShader() {
//                return new RadialGradient(200,200,200, new int[]{0xff12B522,0xff00D6C1}
//                        ,null, Shader.TileMode.CLAMP);
//            }
//        });
//        waveView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (waveView.getCurrentState().getState() == 1) {
//                    waveView.setState(2);
//                } else {
//                    waveView.setState(1);
//                }
//            }
//        });
////        LiveKit.INSTANCE.init(this);
////        Room room = LiveKit.INSTANCE.create(this, new RoomOptions(), new LiveKitOverrides());
////        String url = "wss://testvoice-b8y35yvd.livekit.cloud";
////        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzcwNTg3NjMsImlzcyI6IkFQSVNBTk5tRUdCUXhmWSIsIm5iZiI6MTczNzAyMjc2Mywic3ViIjoiZ3FsaSIsInZpZGVvIjp7ImNhblB1Ymxpc2giOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlLCJjYW5TdWJzY3JpYmUiOnRydWUsInJvb20iOiJ0ZXN0cm9vbSIsInJvb21Kb2luIjp0cnVlfX0.UJQVhxwY0fyHZHj6juu0nvDfMiMdT05tZUtAHLsNTXw";
////
////        room.connect(url, token, new ConnectOptions(), null);
////        // 示例：开启麦克风和摄像头
////        room.getLocalParticipant().setCameraEnabled(true, null);
////        room.getLocalParticipant().setMicrophoneEnabled(true,null);
//
//
//    }
//    @Override
//    protected void onDestroy() {
//        isAlive = false;
//        mMediaRecorder.release();
//        mMediaRecorder = null;
//        super.onDestroy();
//    }
//
//    @Override
//    public void run() {
//        while (isAlive) {
//            handler.sendEmptyMessage(0);
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//}
