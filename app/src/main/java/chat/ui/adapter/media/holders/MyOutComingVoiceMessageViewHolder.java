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
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;

/*
 * Created by troy379 on 05.04.17.
 */
public class MyOutComingVoiceMessageViewHolder
        extends MyOutComingMassageHandler {

    private TextView tvDuration;
    private TextView messageText;
    public MyOutComingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        messageText = itemView.findViewById(R.id.tv_message);

    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        myOutcomingAudioFooterView.setVoiceVisible(true);
    }

}
