package chat.ui.adapter.media.holders;

import android.view.View;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import io.noties.markwon.Markwon;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyInComingMassageHandler extends MessageHolders.IncomingTextMessageViewHolder<Message> {

    public MyInComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        Markwon markwon = Markwon.builder(MyApplication.getInstance()).build();
        markwon.setMarkdown(text, message.getText());
        LogUtil.i(message.getText());
    }
}