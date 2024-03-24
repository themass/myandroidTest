package chat.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.data.DBManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import chat.ui.data.model.ChatHistory;
import chat.asrassistant.model.ChatMessageBean;
import chat.ui.data.fixtures.MessagesFixtures;
import chat.ui.data.model.Message;
import chat.ui.data.model.SimpleMessage;
import chat.ui.data.model.User;
import chat.ui.utils.AppUtils;
import de.hdodenhof.circleimageview.CircleImageView;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;
import tz.co.hosannahighertech.messagekit.messages.MessageInput;
import tz.co.hosannahighertech.messagekit.messages.MessagesList;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

public class CustomLayoutMessagesActivity extends DemoMessagesActivity
        implements MessageInput.InputListener,
        MessageInput.AttachmentsListener {

    public static void open(Context context) {
        context.startActivity(new Intent(context, CustomLayoutMessagesActivity.class));
    }

    private MessagesList messagesList;
    BaseService baseService;
    private List<SimpleMessage> history = new ArrayList<>();
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<Choice>() {
        @Override
        public void onResponse(Choice vo) {
            Message holdMsg = new Message(vo.getId(), gpt, vo.getMessage().getContent());
            LogUtil.i(vo);
            history.add(new SimpleMessage(holdMsg.getText(),"assistant"));
            if(!StringUtils.isEmpty(vo.getMessage().getContent())){
                ChatLog relog = new ChatLog();
                relog.content = vo.getMessage().getContent();
                relog.who = ChatMessageBean.TYPE_RECEIVED;
                relog.setChatId(vo.getId());
                relog.createTime = new Date();
                DBManager.getInstance().saveChatLog(relog);
            }
            messagesAdapter.update(holdMsg);
        }
    };
    User my = new User( "0",MessagesFixtures.names.get(0), "https://img.zcool.cn/community/0160ad5dccabf4a8012129e2104360.jpg@1280w_1l_2o_100sh.jpg", true);
    User gpt = new User( "110",MessagesFixtures.names.get(0), "https://img2.baidu.com/it/u=2884927241,1894596673&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500", true);
    private static String TAG="chat";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layout_messages);

    }
    @Override
    public void setupView() {
        super.setupView();
        messagesList = (MessagesList) findViewById(R.id.messagesList);
        initAdapter();

        MessageInput input = (MessageInput) findViewById(R.id.input);
        input.setInputListener(this);
        input.setAttachmentsListener(this);
        baseService = new BaseService();
        baseService.setup(this);
    }
    @Override
    public void onSelectionChanged(int count) {
        super.onSelectionChanged(count);
        AppUtils.showToast(this, getString(R.string.on_log_selected_messages, count), false);
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        if (!input.toString().isEmpty()) {
            Message myMsg = new Message(MessagesFixtures.getRandomId(), my, input.toString());
            messagesAdapter.addToStart(myMsg, true);
            LogUtil.i(input.toString());
            Message holdMsg = new Message(MessagesFixtures.getRandomId(), gpt, getString(R.string.hold_please));
            messagesAdapter.addToStart(holdMsg, true);
            ChatLog relog = new ChatLog();
            relog.content = input.toString();
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            DBManager.getInstance().saveChatLog(relog);
            history.add(new SimpleMessage(myMsg.getText(),"user"));
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setContent(GsonUtils.getInstance().toJson(history));
            chatHistory.setId(holdMsg.getId());
            baseService.postData(String.format(Constants.getUrl(Constants.CHAT_URL)),chatHistory, listener, new CommonResponse.ResponseErrorListener() {
                @Override
                protected void onError() {
                    super.onError();
                }
            }, TAG, Choice.class);
        }
        return true;
    }

    @Override
    public void onAddAttachments() {
//        messagesAdapter.addToStart(MessagesFixtures.getImageMessage(), true);
    }

    private void initAdapter() {
        MessageHolders holdersConfig = new MessageHolders()
                .setIncomingTextLayout(R.layout.item_custom_incoming_text_message)
                .setOutcomingTextLayout(R.layout.item_custom_outcoming_text_message)
                .setIncomingImageLayout(R.layout.item_custom_incoming_image_message)
                .setOutcomingImageLayout(R.layout.item_custom_outcoming_image_message);
        holdersConfig.setOutcomingTextConfig(MyOutComingMassageHandler.class, R.layout.item_custom_outcoming_text_message);

        messagesAdapter = new MessagesListAdapter<>(super.senderId, holdersConfig, super.imageLoader);
        messagesAdapter.enableSelectionMode(this);
        messagesAdapter.setLoadMoreListener(this);
        messagesList.setAdapter(messagesAdapter);
        List<ChatLog> chatlogs = DBManager.getInstance().getDaoSession().getChatLogDao().loadAll();
        for (ChatLog log:chatlogs){
            if(log.getWho() != ChatMessageBean.TYPE_SYSTEM) {
                Message holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? gpt :  my, log.content, log.getCreateTime());
                messagesAdapter.addToStart(holdMsg, true);
            }
        }
    }
    public static class MyOutComingMassageHandler extends MessageHolders.OutcomingTextMessageViewHolder<IMessage> {
        CircleImageView messageUserAvatar;
        public MyOutComingMassageHandler(View itemView, Object payload) {
            super(itemView, payload);
            messageUserAvatar = (CircleImageView) itemView.findViewById(R.id.messageUserAvatar);
        }

        @Override
        public void onBind(IMessage message) {
            super.onBind(message);
            imageLoader.loadImage(messageUserAvatar,message.getUser().getAvatar(),null);
        }
    }
}