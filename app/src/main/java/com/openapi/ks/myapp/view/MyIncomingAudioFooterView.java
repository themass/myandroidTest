package com.openapi.ks.myapp.view;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.openapi.core.event.FavoriteVo;
import com.openapi.core.util.EventBusUtil;
import com.openapi.core.util.FavoriteUtil;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.StringUtils;
import com.openapi.core.util.SystemUtils;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.view.view.widget.MediaManager;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.config.ChatItemDelEvent;
import com.openapi.ks.myapp.data.config.TtsLoadEvent;

import java.util.function.Function;

import chat.ui.data.model.Message;
import chat.ui.view.FeedbackDialog;

public class MyIncomingAudioFooterView extends LinearLayout implements FavoriteUtil.ModFavoriteListener, FavoriteUtil.GetFavoriteListener, FeedbackDialog.OnFeedbackSubmitListener {
    public LinearLayout llVoice;
    public ImageView ibUp;
    public ImageView ibDown;
    public Message message;
    public ImageView copy;
    public ImageView del;
    private Context context;
    public ImageView ivAudioImg;
    public static ImageView ivAudio;
    public MyIncomingAudioFooterView(@NonNull Context context) {
        super(context);
        setupView(context);
    }

    public MyIncomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setupView(context);
    }

    public MyIncomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView(context);
    }

    public MyIncomingAudioFooterView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupView(context);
    }
    public void setMessage(Message message){
        this.message = message;
        FavoriteUtil.getLocalFavoritesAsync(getContext(), message.getId(), this);
    }
    private void setupView(Context context) {
        this.context = context;
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.item_custom_incoming_footer, null);
        addView(myView);
        llVoice = findViewById(R.id.ll_voice);
        ivAudioImg = findViewById(R.id.ib_audio);
        ibUp = findViewById(R.id.ib_up);
        ibDown = findViewById(R.id.ib_down);
        copy = findViewById(R.id.iv_copy);
        del = findViewById(R.id.iv_del);
        del.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatItemDelEvent chatItemDelEvent = new ChatItemDelEvent();
                chatItemDelEvent.message = message;
                EventBusUtil.getEventBus().post(chatItemDelEvent);
            }
        });
        copy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ToastUtil.showShort(R.string.copied_message);
                SystemUtils.copy(context, message.getText());
            }
        });
        ibUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FavoriteVo vo = new FavoriteVo();
                vo.setType(Constants.FavoriteType.CHAT_OK);
                vo.setItemUrl(message.getId());
                vo.setName("chat");
                FavoriteUtil.modLocalFavoritesAsync(getContext(), vo, MyIncomingAudioFooterView.this);
            }
        });
        ibDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FeedbackDialog feebackDialog = new FeedbackDialog(context, message);
                feebackDialog.setListener(MyIncomingAudioFooterView.this);
                feebackDialog.show();
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

    public void click(View view) {
        LogUtil.i("msg clicked " + message.getId()+"; url:"+message.getVoice().getUrl());
        try {
            if(StringUtils.isEmpty(message.getVoice().getUrl())){
                ivAudioImg.setBackground(null);
                Glide.with(this)
                        .asGif()
                        .load(R.drawable.loading) // 替换为你的GIF文件资源ID
                        .into(ivAudioImg);
                TtsLoadEvent ttsLoadEvent = new TtsLoadEvent();
                ttsLoadEvent.message = message;
                ttsLoadEvent.function = new Function<Message, Void>() {
                    @Override
                    public Void apply(Message message) {
                        LogUtil.i("tts 回调");
                        ivAudioImg.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                        ivAudioImg.setImageResource(0);
                        llVoice.setClickable(true);
                        play();
                        return null;
                    }
                };
                EventBusUtil.getEventBus().post(ttsLoadEvent);
                LogUtil.i("post event:"+ttsLoadEvent.message.getText());
                llVoice.setClickable(false);
                return;
            }
            play();
        }catch (Exception e){
            LogUtil.e(e);
        }
    }
    public void play(){
        try{

            if (MediaManager.isStart()) {
                if (MyOutcomingAudioFooterView.ivAudio != null) {
                    MyOutcomingAudioFooterView.ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
                    MyOutcomingAudioFooterView.ivAudio = null;
                    MediaManager.reset();
                }
            }
            if (ivAudio != null && ivAudio == llVoice.findViewById(R.id.ib_audio)) {
                MediaManager.reset();
                ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                ivAudio = null;
                return;
            }

            if (ivAudio != null) {
                ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                ivAudio = null;
                MediaManager.reset();
            }
            ivAudio = llVoice.findViewById(R.id.ib_audio);
            MediaManager.reset();
            ivAudio.setBackgroundResource(R.drawable.audio_animation_left_list);
            AnimationDrawable drawable = (AnimationDrawable) ivAudio.getBackground();
            drawable.start();
            MediaManager.playSound(MyApplication.getInstance(), message.getVoice().getUrl(), new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    LogUtil.i("play over");
                    try {
                        ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                        ivAudio = null;
                        MediaManager.release();
                    }catch (Exception e){
                        LogUtil.e(e);
                    }
                }
            });
        }catch (Exception e){
            LogUtil.e(e);
        }
    }
    @Override
    public void modFavorite(FavoriteVo vo) {
        LogUtil.i("click "+ message.getId());
        modFavoriteBg(vo);
    }

    @Override
    public void isFavorite(String itemUrl, FavoriteVo vo) {
        modFavoriteBg(vo);
    }
    public void modFavoriteBg(FavoriteVo vo) {
        if (vo == null) {
            ibUp.setImageResource(R.drawable.src_assets_images_like);
            ibDown.setImageResource(R.drawable.src_assets_images_dislike);
        } else {
            if(vo.getType() == Constants.FavoriteType.CHAT_OK){
                ibUp.setImageResource(R.drawable.src_assets_images_liked);
                ibDown.setImageResource(R.drawable.src_assets_images_dislike);
            }else{
                ibUp.setImageResource(R.drawable.src_assets_images_like);
                ibDown.setImageResource(R.drawable.src_assets_images_disliked);
            }
        }
    }

    @Override
    public void onSubmit(Message message) {
        FavoriteVo vo = new FavoriteVo();
        vo.setType(Constants.FavoriteType.CHAT_FAIL);
        vo.setItemUrl(message.getId());
        vo.setName("chatFail");
        FavoriteUtil.addFavorite(context, vo);
        modFavoriteBg(vo);
    }
}
