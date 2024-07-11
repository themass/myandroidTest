package chat.ui.adapter.media.holders;

import android.view.View;

import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyOutComingMassageHandler extends MessageHolders.OutcomingTextMessageViewHolder<Message> {
    CircleImageView messageUserAvatar;

    public MyOutComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
        messageUserAvatar = (CircleImageView) itemView.findViewById(R.id.messageUserAvatar);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            if (Constants.SEX_M.equals(vo.sex)) {
                messageUserAvatar.setImageResource(R.drawable.ic_default_nan);
            } else {
                messageUserAvatar.setImageResource(R.drawable.ic_default_nv);
            }
        } else {
            messageUserAvatar.setImageResource(R.drawable.ic_default_nan);
        }
//            imageLoader.loadImage(messageUserAvatar,message.getUser().getAvatar(),null);
    }
}