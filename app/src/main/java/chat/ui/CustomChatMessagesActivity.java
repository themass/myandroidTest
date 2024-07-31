package chat.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.hrl.chaui.widget.RecordButton;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.entity.LocalMedia;
import com.openapi.commons.common.ui.view.InputRecordView;
import com.openapi.commons.common.ui.view.Mp3ToWav;
import com.openapi.commons.common.util.DoubleClickExit;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.data.config.ConfigActionJump;
import com.openapi.ks.myapp.service.LogUploadService;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.app.BaseDrawerActivity;
import com.openapi.ks.myapp.ui.base.app.BasePermissionActivity;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.myapp.ui.user.LoginActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import chat.ui.adapter.MyMessagesListAdapter;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.ChatHistory;
import chat.ui.data.fixtures.MessagesFixtures;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;
import chat.ui.data.model.User;
import tz.co.hosannahighertech.messagekit.commons.ImageLoader;
import tz.co.hosannahighertech.messagekit.messages.MessagesList;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

public class CustomChatMessagesActivity extends BaseDrawerActivity implements MessagesListAdapter.SelectionListener,
        MessagesListAdapter.OnLoadMoreListener{

    @BindView(R.id.messagesList)
    MessagesList messagesList;
    @BindView(R.id.input)
    InputRecordView input;
    @BindView(R.id.fl_banner)
    FrameLayout banner;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
    public Message holdMsg = null;
    public ChatLog relogLlm = null;
    public List<SimpleMessage> history = new ArrayList<>();

    protected final String senderId = "0";
    protected ImageLoader imageLoader;
    protected MyMessagesListAdapter messagesAdapter;
    public Long sessionId = 0l;
    private Menu menu;
    public int selectionCount;

    public List<MsgContent> msgContents = new ArrayList<>();


    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<Choice>() {
        @Override
        public void onResponse(Choice vo) {
            holdMsg = new Message(vo.getId(), User.gpt, vo.getMessage().getContent());
            LogUtil.i(vo);
            history.add(new SimpleMessage(holdMsg.getText(), "assistant"));
            if (!StringUtils.isEmpty(vo.getMessage().getContent())) {
                MsgContent msgContent = new MsgContent();
                msgContent.setContent(vo.getMessage().getContent());
                ChatLog relog = new ChatLog();
                relog.content = GsonUtils.getInstance().toJson(msgContent);
                relog.who = ChatMessageBean.TYPE_RECEIVED;
                relog.setChatId(vo.getId());
                relog.createTime = new Date();
                relog.sessionId = sessionId;
                DBManager.getInstance().saveChatLog(relog);
                relogLlm = relog;
            }else {
                relogLlm = null;
            }
            messagesAdapter.update(holdMsg);
            llmCallBack(vo.getMessage().getContent());
        }
    };
    public static String TAG = "chat";
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                messagesAdapter.deleteSelectedMessages();
                break;
            case R.id.action_copy:
                messagesAdapter.copySelectedMessagesText(this, getMessageStringFormatter(), true);
                ToastUtil.showLong(R.string.copied_message);
                break;
            case R.id.action_chat_session:
                createNewChatSession();
                break;
        }
        return true;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ChatSessionEvent event) {
        PreferenceUtils.setSettingLong(this, Constants.CHAT_SESSION, event.id);
        messagesAdapter.clear(true);
        loadMessages();
    }

    private void createNewChatSession() {
        // 一个自定义的布局，作为显示的内容
        View contentView = LayoutInflater.from(this).inflate(
                R.layout.chat_new, null);
        final PopupWindow popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setTouchable(true);
        popupWindow.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });

        popupWindow.showAtLocation(this.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
//        popupWindow.showAtLocation(findViewById(R.id.fl_body), Gravity.CENTER, screenWidth / 2, screenHeight / 2);
        EditText nameView = contentView.findViewById(R.id.chat_name);
        // 设置按钮的点击事件
        Button btnDel = (Button) contentView.findViewById(R.id.chat_cancel);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
            }
        });
        Button btnOk = (Button) contentView.findViewById(R.id.chat_ok);
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nameView.getText().toString().isEmpty()) {
                    LogUtil.i("没有填名称");
                    return;
                }
                ChatSessionLog log = new ChatSessionLog();
                log.setName(nameView.getText().toString());
                log.setCreateTime(new Date());
                log.setSetting("");
                DBManager.getInstance().getDaoSession().getChatSessionLogDao().insert(log);
                onEvent(new ChatSessionEvent(log.id));
                ToastUtil.showShort(R.string.save_ok);
                LogUtil.i("对话名称：" + nameView.getText().toString() + "---" + log.id);
                popupWindow.dismiss();
            }
        });
    }
    @Override
    public void onSelectionChanged(int count) {
        this.selectionCount = count;
        menu.findItem(R.id.action_delete).setVisible(count > 0);
        menu.findItem(R.id.action_copy).setVisible(count > 0);
        ToastUtil.showShort(getString(R.string.on_log_selected_messages, count));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layout_messages);
        EventBusUtil.getEventBus().register(this);
        AdsContext.showNext(this);
        initBanner();
    }

    private void initBanner() {
        BannerHeaderFragment myFragment = BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN);
        FragmentManager fragmentManager = getSupportFragmentManager();
        // 开始Fragment事务
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        // 将Fragment添加到FrameLayout中
        fragmentTransaction.add(R.id.fl_banner, myFragment);
        // 提交事务
        fragmentTransaction.commit();
    }

    @Override
    public void setupView() {
        super.setupView();
        showToolbar(true);
        initAdapter();
        input.setAudioFinishListener(new RecordButton.OnFinishedRecordListener() {
            @Override
            public void onFinishedRecord(String audioPath, int time) {
                LogUtil.i("录音结束回调");
                File file = new File(audioPath);
                if (file.exists()) {
                    sendAudioMessage(audioPath, time);
                }
            }
        });
        input.setPicSelectListener(new InputRecordView.PicSelectListener() {
            @Override
            public void sendMessage(String content) {
                sendTextMsg(content);
            }

            @Override
            public Activity picSelect() {
                return CustomChatMessagesActivity.this;
            }
        });
    }

    private void initAdapter() {
        messagesAdapter = new MyMessagesListAdapter(senderId);
        messagesAdapter.enableSelectionMode(this);
        messagesAdapter.setLoadMoreListener(this);
        messagesList.setAdapter(messagesAdapter);
        messagesList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                input.closeKeypad();
                return false;
            }
        });
        loadMessages();

    }

    public void loadMessages() {
        sessionId = PreferenceUtils.getPrefLong(this, Constants.CHAT_SESSION, 0);
        LogUtil.i("loadMessages sessionId=" + sessionId);
        List<ChatLog> chatlogs = DBManager.getInstance().getDaoSession().getChatLogDao().queryRaw(" where SESSION_ID=" + sessionId);
        for (ChatLog log : chatlogs) {
            if (log.getWho() != ChatMessageBean.TYPE_SYSTEM) {
                MsgContent msgContent = GsonUtils.getObj(log.content, MsgContent.class);
                Message holdMsg = new Message();
                if (log.msgType == MessageType.TXT_TYPE) {
                    holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? User.gpt : User.my, msgContent.getContent(), log.getCreateTime());
                } else if (log.msgType == MessageType.AUDIO_TYPE) {
                    holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? User.gpt : User.my, msgContent.getOrg(), log.getCreateTime());
                    holdMsg.setVoice(new Message.Voice(msgContent.getContent(), msgContent.getTime()));
                }
                if (log.msgType == MessageType.IMAGE_TYPE) {

                }
                holdMsg.setMsgType(log.getMsgType());
                messagesAdapter.addToStart(holdMsg, true);
            }
        }
        ChatSessionLog sessionLog = DBManager.getInstance().getDaoSession().getChatSessionLogDao().load(sessionId);
        if (sessionLog != null)
            setToolbarTitle(sessionLog.getName(), false);
    }

    protected MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
        return message -> {
            String text = message.getText();
            return text;
        };
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
//        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtil.i("onActivityResult");
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case InputRecordView.REQUEST_CODE_FILE:
//                    String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
//                    LogUtil.d("获取到的文件路径:"+filePath);
//                    sendFileMessage(mSenderId, mTargetId, filePath);
                    break;
                case InputRecordView.REQUEST_CODE_IMAGE:
                    // 图片选择结果回调
                    List<LocalMedia> selectListPic = PictureSelector.obtainSelectorList(data);
                    for (LocalMedia media : selectListPic) {
                        LogUtil.i("获取图片路径成功:" + media.getPath());
                        sendImageMessage(media);
                    }
                    break;
                case InputRecordView.REQUEST_CODE_VEDIO:
                    // 视频选择结果回调
                    List<LocalMedia> selectListVideo = PictureSelector.obtainSelectorList(data);
                    for (LocalMedia media : selectListVideo) {
                        LogUtil.i("获取视频路径成功:" + media.getPath());
                        sendVideoMessage(media);
                    }
                    break;
            }
        }
    }

    //文本消息
    public void sendTextMsg(String hello) {
        //开始发送
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        String content = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.MY_SETTING, "");
        if (!StringUtils.isEmpty(hello)) {
            MsgContent msgContent = new MsgContent();
            msgContent.setContent(hello);
            Message myMsg = new Message(MessagesFixtures.getRandomId(), User.my, hello);
            messagesAdapter.addToStart(myMsg, true);
            LogUtil.i("发送消息： " + hello);
            Message holdMsg = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            messagesAdapter.addToStart(holdMsg, true);
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);
            history.add(new SimpleMessage(myMsg.getText(), "user"));
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setContent(GsonUtils.getInstance().toJson(history));
            chatHistory.setId(holdMsg.getId());
            chatHistory.setCharater(content);
            baseService.postData(String.format(Constants.getUrl(Constants.CHAT_URL)), chatHistory, listener, new CommonResponse.ResponseErrorListener() {
                @Override
                protected void onError() {
                    super.onError();
                }
            }, TAG, Choice.class);
//            AdsContext.showRand(this);
        }
    }


    //图片消息
    private void sendImageMessage(final LocalMedia media) {
    }


    //视频消息
    private void sendVideoMessage(final LocalMedia media) {
    }

    //文件消息
    private void sendFileMessage(String from, String to, final String path) {
    }

    //语音消息
    private void sendAudioMessage(final String path, int time) {
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        String wavPath = path.replace("mp3", "wav");
        Mp3ToWav.convertMP3ToPCM(path, wavPath);
        String asr = path;
        if (!StringUtils.isEmpty(path)) {
            Message myMsg = new Message(MessagesFixtures.getRandomId(), User.my, null);
            myMsg.setMsgType(MessageType.AUDIO_TYPE);
            myMsg.setVoice(new Message.Voice(path, time));
            messagesAdapter.addToStart(myMsg, true);

            Message holdMsg = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            messagesAdapter.addToStart(holdMsg, true);

            MsgContent msgContent = new MsgContent();
            msgContent.setContent(path);
            msgContent.setOrg(asr);
            msgContent.setTime(time);
            msgContent.setMsgId(myMsg.getId());
            msgContent.setHoldId(holdMsg.getId());
            msgContents.add(msgContent);

            recognizeFile(wavPath, msgContent);
        }
    }


    public void recognizeFile(String filePath, MsgContent msgContent) {
    }
    public void llmCallBack(String text){}

    @Override
    public void onLoadMore(int page, int totalItemsCount) {

    }
}
