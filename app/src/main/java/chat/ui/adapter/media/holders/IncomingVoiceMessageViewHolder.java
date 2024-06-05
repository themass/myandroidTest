package chat.ui.adapter.media.holders;

import android.view.View;
import android.widget.TextView;

import com.openapi.ks.chatfree.R;

import chat.ui.data.model.Message;
import chat.ui.utils.FormatUtils;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;
import tz.co.hosannahighertech.messagekit.utils.DateFormatter;


/*
 * Created by troy379 on 05.04.17.
 */
public class IncomingVoiceMessageViewHolder
        extends MessageHolders.IncomingTextMessageViewHolder<Message> {

    private TextView tvDuration;
    private TextView tvTime;

    public IncomingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        tvTime = itemView.findViewById(R.id.time);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        tvTime.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
    }
}
