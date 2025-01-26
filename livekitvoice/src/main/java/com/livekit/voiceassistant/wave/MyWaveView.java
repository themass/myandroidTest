package com.livekit.voiceassistant.wave;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.aghajari.waveanimation.AXLineWaveView;
import com.aghajari.waveanimation.AXWaveView;

import io.livekit.android.compose.state.VoiceAssistant;
import io.livekit.android.example.voiceassistant.R;

/**
 * Created by openapi on 2016/9/6.
 */
public class MyWaveView extends LinearLayout {

    AXWaveView circleWave;
    AXLineWaveView lineWave;
    VoiceAssistant voiceAssistant;
    public MyWaveView(Context context, VoiceAssistant assistant) {
        super(context);
        this.voiceAssistant = assistant;
        setupView();
    }

    public MyWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }
    public void initTrace(VoiceAssistant voiceAssistant){
    }
    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.wave_view, null);
        addView(myView);
        circleWave = findViewById(R.id.circle_wave);
        lineWave = findViewById(R.id.line_wave);
    }
}
