package com.openapi.ks.myapp.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openai.commonevent.FavoriteVo;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.FavoriteUtil;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.StringUtils;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.ToastUtil;
import com.openai.commonview.view.widget.MediaManager;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.data.config.ChatItemDelEvent;

import chat.ui.data.model.Message;

public class MyOutcomingAudioFooterView extends LinearLayout {
    public RelativeLayout llVoice;
    public Message message;
    public ImageView copy;
    public ImageView del;
    private Context context;
    public MyOutcomingAudioFooterView(@NonNull Context context) {
        super(context);
        setupView(context);
    }

    public MyOutcomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setupView(context);
    }

    public MyOutcomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView(context);
    }

    public MyOutcomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupView(context);
    }
    public void setMessage(Message message){
        this.message = message;
    }
    private void setupView(Context context) {
        this.context = context;
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.item_custom_outcoming_footer, null);
        addView(myView);
        llVoice = findViewById(R.id.ll_voice);
        del = findViewById(R.id.iv_del);
        del.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatItemDelEvent chatItemDelEvent = new ChatItemDelEvent();
                chatItemDelEvent.message = message;
                EventBusUtil.getEventBus().post(chatItemDelEvent);
            }
        });
        copy = findViewById(R.id.iv_copy);
        copy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ToastUtil.showShort(R.string.copied_message);
                SystemUtils.copy(context, message.getText());
            }
        });
        llVoice.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                click(v);
            }
        });
    }
    public void setVoiceVisible(boolean voiceVisible){
        if(voiceVisible) {
            llVoice.setVisibility(VISIBLE);
        }else{
            llVoice.setVisibility(GONE);
        }
    }
    public static ImageView ivAudio;
    public void click(View view) {
        LogUtil.i("msg clicked " + message.getId());
        try {
            if (StringUtils.isEmpty(message.getVoice().getUrl())) {
                return;
            }
            if (MediaManager.isStart()) {
                if (MyIncomingAudioFooterView.ivAudio != null) {
                    MyIncomingAudioFooterView.ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                    MyIncomingAudioFooterView.ivAudio = null;
                    MediaManager.reset();
                }
            }
            if (ivAudio != null && ivAudio == view.findViewById(R.id.ib_audio)) {
                MediaManager.reset();
                ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
                ivAudio = null;
                return;
            }

            if (ivAudio != null) {
                ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
                ivAudio = null;
                MediaManager.reset();
            }
            ivAudio = view.findViewById(R.id.ib_audio);
            MediaManager.reset();
            ivAudio.setBackgroundResource(R.drawable.audio_animation_right_list);
            AnimationDrawable drawable = (AnimationDrawable) ivAudio.getBackground();
            drawable.start();
            MediaManager.playSound(MyApplication.getInstance(), message.getVoice().getUrl(), new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    LogUtil.i("play over");
                    ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
                    ivAudio = null;
                    MediaManager.release();
                }
            });
        }catch (Exception e){
            LogUtil.e(e);
        }
    }

}
