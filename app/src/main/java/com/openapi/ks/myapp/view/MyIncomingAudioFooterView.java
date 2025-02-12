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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
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
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.config.TtsLoadEvent;

import java.util.function.Function;

import chat.ui.adapter.media.holders.MyOutComingVoiceMessageViewHolder;
import chat.ui.data.model.Message;
import chat.ui.fragment.FeedbackDialog;

public class MyIncomingAudioFooterView extends LinearLayout implements FavoriteUtil.ModFavoriteListener, FavoriteUtil.GetFavoriteListener, FeedbackDialog.OnFeedbackSubmitListener {
    public LinearLayout llVoice;
    public ImageView ibUp;
    public ImageView ibDown;
    public Message message;
    public ImageView copy;
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
                    ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                    ivAudio = null;
                    MediaManager.release();
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
            ibUp.setBackgroundResource(R.drawable.ic_up);
            ibDown.setBackgroundResource(R.drawable.ic_down);
        } else {
            if(vo.getType() == Constants.FavoriteType.CHAT_OK){
                ibUp.setBackgroundResource(R.drawable.ic_up_ok);
                ibDown.setBackgroundResource(R.drawable.ic_down);
            }else{
                ibUp.setBackgroundResource(R.drawable.ic_up);
                ibDown.setBackgroundResource(R.drawable.ic_down_ok);
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
