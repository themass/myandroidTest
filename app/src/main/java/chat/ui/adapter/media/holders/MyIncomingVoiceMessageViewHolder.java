package chat.ui.adapter.media.holders;

import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.openai.commonview.view.widget.MediaManager;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.StringUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.data.model.Message;
import com.openai.commonutil.FormatUtils;

import tz.co.hosannahighertech.messagekit.utils.DateFormatter;


/*
 * Created by troy379 on 05.04.17.
 */
public class MyIncomingVoiceMessageViewHolder
        extends MyInComingMassageHandler {

    private TextView tvDuration;
    private TextView tvTime;
    private LinearLayout llSound;

    public MyIncomingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        tvTime = itemView.findViewById(R.id.messageTime);
        llSound = itemView.findViewById(R.id.ll_sound);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        LogUtil.i("获取 文件长度：" + message.getVoice().getDuration());
        tvTime.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
        llSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click(v, message);
            }
        });
    }
    public static void onLongClick(View view, Message msg) {
//        TextView messageText = view.findViewById(R.id.tv_message);
//        if(!StringUtils.isEmpty(msg.getText())){
//            messageText.setText(msg.getText());
//            messageText.setVisibility(View.VISIBLE);
//        }else{
//            messageText.setVisibility(View.GONE);
//        }
    }
    public static ImageButton ivAudio;
    public static void click(View view, Message message) {
        LogUtil.i("msg clicked " + message);
        try {
            if (StringUtils.isEmpty(message.getVoice().getUrl())) {
                return;
            }
            if (MediaManager.isStart()) {
                if (MyOutComingVoiceMessageViewHolder.ivAudio != null) {
                    MyOutComingVoiceMessageViewHolder.ivAudio.setBackgroundResource(R.drawable.audio_animation_list_right_3);
                    MyOutComingVoiceMessageViewHolder.ivAudio = null;
                    MediaManager.reset();
                }
            }
            if (ivAudio != null && ivAudio == view.findViewById(R.id.ib_audio)) {
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
            ivAudio = view.findViewById(R.id.ib_audio);
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
}
