package chat.ui.adapter.media.holders;

import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.hrl.chaui.widget.MediaManager;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.ks.chatfree.R;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.BaseChatMessagesActivity;
import chat.ui.data.model.Message;
import chat.ui.utils.FormatUtils;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;
import tz.co.hosannahighertech.messagekit.utils.DateFormatter;

/*
 * Created by troy379 on 05.04.17.
 */
public class OutcomingVoiceMessageViewHolder
        extends MyOutComingMassageHandler {

    private TextView tvDuration;
    private TextView messageText;

    public OutcomingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        messageText = itemView.findViewById(R.id.tv_message);

    }

    public static void onLongClick(View view, Message msg) {
        TextView messageText = view.findViewById(R.id.tv_message);
        if(!StringUtils.isEmpty(msg.getText())){
            messageText.setText(msg.getText());
            messageText.setVisibility(View.VISIBLE);
        }else{
            messageText.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        messageText.setVisibility(View.GONE);
    }

    private static ImageButton ivAudio;

    public static void click(View view, Message message) {
        LogUtil.i("msg clicked " + message);
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
