package chat.ui.adapter.media.holders;

import android.view.View;
import android.widget.RelativeLayout;

import androidx.fragment.app.FragmentActivity;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.AdsManager;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;
import com.openapi.ks.myapp.view.MyOutcomingAudioFooterView;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyOutComingMassageHandler extends MessageHolders.OutcomingTextMessageViewHolder<Message> {
    public CircleImageView messageUserAvatar;
    public Message message;
    public RelativeLayout rvAds;
    public MyOutcomingAudioFooterView myOutcomingAudioFooterView;
    public MyOutComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
        messageUserAvatar = (CircleImageView) itemView.findViewById(R.id.messageUserAvatar);
        myOutcomingAudioFooterView = itemView.findViewById(R.id.ll_sound);
        rvAds = itemView.findViewById(R.id.rv_ads);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        this.message = message;
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
        myOutcomingAudioFooterView.setMessage(message);
//        try {
//            if (rvAds != null) {
//                if (Boolean.TRUE.equals(message.showAds)) {
//                    rvAds.setVisibility(View.VISIBLE);
//                    AdsManager.getInstans().showBannerAds((FragmentActivity) messageUserAvatar.getContext(), rvAds, AdsContext.Categrey.CATEGREY_VPN2);
//                } else {
//                    rvAds.removeAllViews();
//                    rvAds.setVisibility(View.GONE);
//                }
//            }
//        }catch (Exception e){
//            LogUtil.e("",e);
//        }
    }
}