package chat.ui.adapter.media.holders;

import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.fragment.app.FragmentActivity;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.AdsManager;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;

import org.markdown4j.Markdown4jProcessor;

import java.io.IOException;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import es.dmoral.markdownview.Config;
import es.dmoral.markdownview.MarkdownView;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyInComingMassageHandler extends MessageHolders.IncomingTextMessageViewHolder<Message> {
    public MarkdownView composeView ;
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
        WebSettings webSettings = composeView.getSettings();
        // 设置默认字体大小
        webSettings.setDefaultFontSize(16);
        composeView.setCurrentConfig(new Config(
                "file:///android_asset/markdown.css",
                Config.CssCodeHighlight.MONOKAI_SUBLIME // This can be a custom one too, but there are already added some options.
        ));
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        this.message = message;
        // 设置 ComposeView 的内容
        String html = message.getText().replace("[assistant]:","").replace("[assistant]","");
//        try {
//            html = new Markdown4jProcessor().process(message.getText());
//        }catch (Exception e){
//
//        }

        composeView.loadFromText(html);
        LogUtil.i("html 内容="+html);
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