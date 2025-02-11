package chat.ui.adapter.media.holders;

import android.view.View;
import android.widget.TextView;

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

    public MyIncomingVoiceMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        tvDuration = itemView.findViewById(R.id.duration);
        tvTime = itemView.findViewById(R.id.messageTime);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        tvDuration.setText(
                FormatUtils.getDurationString(
                        message.getVoice().getDuration()));
        LogUtil.i("获取 文件长度：" + message.getVoice().getDuration());
        tvTime.setText(DateFormatter.format(message.getCreatedAt(), DateFormatter.Template.TIME));
        myIncomingAudioFooterView.setVoiceVisible(true);
    }


}
