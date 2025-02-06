package chat.ui.adapter.media.holders;

import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;

import org.markdown4j.Markdown4jProcessor;

import java.io.IOException;

import chat.ui.data.model.Message;
import de.hdodenhof.circleimageview.CircleImageView;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;

public class MyInComingMassageHandler extends MessageHolders.IncomingTextMessageViewHolder<Message> {
    WebView composeView ;
    CircleImageView messageUserAvatar;
    public MyInComingMassageHandler(View itemView, Object payload) {
        super(itemView, payload);
        composeView = itemView.findViewById(R.id.markdownMessageText);
        messageUserAvatar = (CircleImageView) itemView.findViewById(R.id.messageUserAvatar);
        WebSettings webSettings = composeView.getSettings();
        // 设置默认字体大小
        webSettings.setDefaultFontSize(16);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
//        RichText.fromMarkdown(message.getText()).into(text);
        // 设置 ComposeView 的内容
        String html = message.getText().replace("[assistant]:","").replace("[assistant]","");
        try {
            html = new Markdown4jProcessor().process(message.getText());
        }catch (Exception e){

        }
        composeView.loadDataWithBaseURL(null,html,"text/html","utf-8",null);
        LogUtil.i("html 内容="+html);
        messageUserAvatar.setImageResource(R.drawable.ai_chat);

    }
}