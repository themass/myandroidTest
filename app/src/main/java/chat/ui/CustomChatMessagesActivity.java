package chat.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.openapi.commons.common.util.DoubleClickExit;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PermissionHelper;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.data.config.ConfigActionJump;
import com.openapi.ks.myapp.data.config.UserLoginEvent;
import com.openapi.ks.myapp.service.LogUploadService;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.myapp.ui.user.LoginActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
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

public class CustomChatMessagesActivity extends BaseChatMessagesActivity
        implements MessageInput.InputListener,
        MessageInput.AttachmentsListener, ActivityCompat.OnRequestPermissionsResultCallback{

    public static void open(Context context) {
        context.startActivity(new Intent(context, CustomChatMessagesActivity.class));
    }

    @BindView(R.id.messagesList)
    MessagesList messagesList;
    @BindView(R.id.input)
    MessageInput input;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
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
                relog.sessionId = sessionId;
                DBManager.getInstance().saveChatLog(relog);
            }
            messagesAdapter.update(holdMsg);
        }
    };
    private static String TAG="chat";
    private ConfigActionJump jump = new ConfigActionJump();
    private PermissionHelper mPermissionHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layout_messages);
        EventBusUtil.getEventBus().register(jump);
        mPermissionHelper = new PermissionHelper(this);
        EventBusUtil.getEventBus().register(this);
        mPermissionHelper.checkNeedPermissions();
        AdsContext.showNext(this);

    }

    @Override
    public void setupView() {
        super.setupView();
        initAdapter();
        input.setInputListener(this);
        input.setAttachmentsListener(this);
    }
    @Override
    public void onSelectionChanged(int count) {
        super.onSelectionChanged(count);
        AppUtils.showToast(this, getString(R.string.on_log_selected_messages, count), false);
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        if(UserLoginUtil.getUserCache()==null){
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
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
            relog.sessionId = sessionId;
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
        ToastUtil.showShort(R.string.tab_vip_temp);
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
        loadMessages();

    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
        mPermissionHelper.checkNeedPermissions();
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyUp");
        if (closeDrawer()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (selectionCount != 0) {
                messagesAdapter.unselectAllItems();
                return true;
            }
            boolean flag = false;
            for (OnBackKeyDownListener l : keyListeners) {
                flag = flag || l.onkeyBackDown();
            }
            if (flag) {
                return true;
            }

            if (!DoubleClickExit.check()) {
                ToastUtil.showShort(getString(R.string.close_over));
                return true;
            } else {
                finish();
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    public void addListener(OnBackKeyDownListener keyListener) {
        keyListeners.add(keyListener);
    }

    public void removeListener(OnBackKeyDownListener keyListener) {
        keyListeners.remove(keyListener);
    }
    @Override
    public void onDestroy() {
        LogUtil.i("main destory");
        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPermissionHelper.checkNeedPermissions();
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
            UserInfoVo vo = UserLoginUtil.getUserCache();
            if (vo != null) {
                if (Constants.SEX_M.equals(vo.sex)) {
                    messageUserAvatar.setImageResource(R.drawable.ic_default_nan);
                } else {
                    messageUserAvatar.setImageResource(R.drawable.ic_default_nv);
                }
            }else {
                messageUserAvatar.setImageResource(R.drawable.ic_default_nan);
            }
//            imageLoader.loadImage(messageUserAvatar,message.getUser().getAvatar(),null);
        }
    }
}
