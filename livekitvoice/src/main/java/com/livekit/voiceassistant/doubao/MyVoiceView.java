package com.livekit.voiceassistant.doubao;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.livekit.voiceassistant.wave.MyWaveView;

import io.livekit.android.example.voiceassistant.R;

public class MyVoiceView extends FrameLayout {
    private RecyclerView recyclerView;
    private MyWaveView myWaveView;
    public MyVoiceView(@NonNull Context context) {
        super(context);
        init();
    }

    public MyVoiceView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyVoiceView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MyVoiceView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    public void init(){
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.activity_voice_assistant, null);
        addView(myView);
        recyclerView = findViewById(R.id.chat_log_recycler_view);
        myWaveView = findViewById(R.id.background_blur_frame);
    }
}
