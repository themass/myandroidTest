package chat.ui.adapter.media.holders;

import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openai.commonview.view.widget.MediaManager;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.data.model.Message;
import com.openai.commonutil.FormatUtils;

/*
 * Created by troy379 on 05.04.17.
 */
public class MyOutComingVoiceMessageViewHolder
        extends MyOutComingMassageHandler {

    private TextView tvDuration;
    private TextView messageText;
    private RelativeLayout llSound;
    public MyOutComingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        messageText = itemView.findViewById(R.id.tv_message);
        llSound = itemView.findViewById(R.id.ll_sound);

    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        llSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click(v, message);
            }
        });
    }

    public static ImageButton ivAudio;

    public static void click(View view, Message message) {
        LogUtil.i("msg clicked " + message);
        if(MediaManager.isStart()){
            if(MyIncomingVoiceMessageViewHolder.ivAudio!=null){
                MyIncomingVoiceMessageViewHolder.ivAudio.setBackgroundResource(R.drawable.audio_animation_list_left_3);
                MyIncomingVoiceMessageViewHolder.ivAudio = null;
                MediaManager.reset();
            }
        }
        if(ivAudio!= null && ivAudio==view.findViewById(R.id.ib_audio)){
            ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
            MediaManager.reset();
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
    }
}
