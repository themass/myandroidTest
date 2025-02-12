package chat.ui.adapter.media.holders;

import android.view.View;
import android.webkit.WebSettings;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;

import java.io.IOException;
import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import io.noties.markwon.Markwon;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyInComingMassageHandler extends MessageHolders.IncomingTextMessageViewHolder<Message> {
    public TextView composeView ;
    public CircleImageView messageUserAvatar;
    public Message message;
    public RelativeLayout rvAds;
    public MyIncomingAudioFooterView myIncomingAudioFooterView;

    public MyInComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
        composeView = itemView.findViewById(R.id.markdownMessageText);
        messageUserAvatar = (CircleImageView) itemView.findViewById(R.id.messageUserAvatar);
        rvAds = itemView.findViewById(R.id.rv_ads);
        myIncomingAudioFooterView = itemView.findViewById(R.id.ll_sound);
//        WebSettings webSettings = composeView.getSettings();
        // 设置默认字体大小
//        webSettings.setDefaultFontSize(16);
//        composeView.setCurrentConfig(new Config(
//                "file:///android_asset/markdown.css",
//                Config.CssCodeHighlight.MONOKAI_SUBLIME // This can be a custom one too, but there are already added some options.
//        ));
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        this.message = message;
        // 设置 ComposeView 的内容
//        String html = "最小两个数的平方和可以用以下表达式表示：\n\n如果有两个最小数 \\(a\\) 和 \\(b\\)，那么它们的平方和为 \\(a^2 + b^2\\)。\n\n如果你有具体的数列，可以告诉我，我可以帮你找出最小的两个数哦！😊";
//        if(!StringUtils.isEmpty(message.getText())) {
//            html = message.getText().replace("[assistant]:", "").replace("[assistant]", "").replace("[ASSISTANT]", "");
//        }
//        composeView.loadFromText(html);
//        try {
//            html = new Markdown4jProcessor().process(html);
//        }catch (Exception e){
//        }
//        composeView.loadData(html,"text/html","utf-8");
        Markwon markwon = Markwon.create(composeView.getContext());

// set markdown
        markwon.setMarkdown(composeView, message.getText());
        LogUtil.i("html 内容="+message.getText());
        messageUserAvatar.setImageResource(R.drawable.ai_chat);
        myIncomingAudioFooterView.setMessage(message);

//        try {
//            if (rvAds != null) {
//                if (Boolean.TRUE.equals(message.showAds)) {
//                    rvAds.setVisibility(View.VISIBLE);
//                    AdsManager.getInstans().showBannerAds((FragmentActivity) composeView.getContext(), rvAds, AdsContext.Categrey.CATEGREY_VPN2);
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