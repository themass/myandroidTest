package chat.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
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

import com.android.volley.Response;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.entity.LocalMedia;
import com.openai.CommonConstants;
import com.openai.ads.yewu.ads.base.AdsManager;
import com.openai.commonevent.ConfigActionEvent;
import com.openai.commonutil.Base64Util;
import com.openai.commonutil.FileUtils;
import com.openai.commonutil.ImageUtils;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.StringUtils;
import com.openai.commonview.view.InputRecordView;
import com.openai.commonutil.Mp3ToWav;
import com.openai.commonutil.DoubleClickExit;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.GsonUtils;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.net.VolleyUtils;
import com.openai.net.request.CommonResponse;
import com.openai.ads.yewu.um.MobAgent;
import com.openai.net.request.GsonInfoListRequest;
import com.openai.net.request.InfoListVo;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.AsrContentForm;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.AsrResponseVo;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ChatItemDelEvent;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.data.config.ConfigActionJump;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.app.BaseDrawerActivity;
import com.openapi.ks.myapp.ui.fragment.AppListFragment;
import com.openapi.ks.myapp.ui.fragment.ChatSessionFragment;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.myapp.ui.user.LoginActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import butterknife.BindView;
import chat.ui.adapter.MyMessagesListAdapter;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.ChatHistory;
import chat.ui.data.fixtures.MessagesFixtures;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.User;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import tz.co.hosannahighertech.messagekit.messages.MessagesList;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;
@SuppressWarnings("unchecked")
public class CustomChatMessagesActivity extends BaseDrawerActivity implements
        MessagesListAdapter.OnLoadMoreListener{

    @BindView(R.id.messagesList)
    MessagesList messagesList;
    @BindView(R.id.input)
    InputRecordView input;
    @BindView(R.id.fl_banner)
    FrameLayout banner;
    private Set<OnBackKeyDownListener> keyListeners = new HashSet<>();
    public ChatLog relogLlm = null;

    protected final String senderId = "0";
    protected MyMessagesListAdapter messagesAdapter;
    public Long sessionId = 0l;
    public String setting = "";

    public String sessionName = "Default";
    public int selectionCount;
    private static String ASRTAG = "ASRTAG";
    private ConfigActionJump jump = new ConfigActionJump();
    public Map<String,MsgContent> msgContents = new HashMap<>();
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<Choice>() {
        @Override
        public void onResponse(Choice vo) {
            LogUtil.i("llm 返回"+ GsonUtils.getInstance().toJson(vo));
            Message holdMsg = new Message(vo.getId(), User.gpt, vo.getMessage().getContent());
            initHoldMsg(holdMsg);
            if (!StringUtils.isEmpty(vo.getMessage().getContent())) {
                MsgContent msgContent = new MsgContent();
                msgContent.setContent(vo.getMessage().getContent());
                ChatLog relog = new ChatLog();
                initLLMMsg(relog,msgContent );
                relog.content = GsonUtils.getInstance().toJson(msgContent);
                LogUtil.i("msgContent:"+ relog.content);
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
            llmCallBack(vo);
        }
    };
    CommonResponse.ResponseOkListener asrListener = new CommonResponse.ResponseOkListener<AsrResponseVo>() {
        @Override
        public void onResponse(AsrResponseVo vo) {
            if(StringUtils.isEmpty(vo.getText())){
                vo.setText("......");
            }
            LogUtil.i("asr data="+vo);
            LogUtil.i("发送消息： " + vo.getText());
            LogUtil.i("msgContents:"+msgContents);
            MsgContent msgContent = msgContents.remove(vo.getId());
            if(msgContent == null){
                return;
            }
            msgContent.setOrg(vo.getText());
            Message sendMsg = messagesAdapter.getMessageByIndex(messagesAdapter.getMessagePositionById(msgContent.getMsgId()));
            sendMsg.setText(vo.getText());
            LogUtil.i("find msg id="+msgContent.getMsgId()+"; sendMsg="+sendMsg+"; msgContent="+msgContent);
            messagesAdapter.update(sendMsg);
            String content = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.MY_SETTING, "");
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.msgType = MessageType.AUDIO_TYPE;
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.setChatId(msgContent.getMsgId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);

            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setText(vo.getText());
            chatHistory.setSessinId(sessionId);
            chatHistory.setId(msgContent.getHoldId());
            chatHistory.setCharater(content);
            baseService.postData(String.format(Constants.getUrl(Constants.CHAT_URL)), chatHistory, listener, new CommonResponse.ResponseErrorListener() {
                @Override
                protected void onError() {
                    super.onError();
                }
            }, TAG, Choice.class);
        }
    };
    public static String TAG = "chat";
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false);
        menu.findItem(R.id.sound_switch).setIcon(needTts?R.drawable.song_play_icon:R.drawable.song_not_play_icon);
        return true;
    }
    // 添加确认删除对话框的方法
    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.del_hint)
                .setMessage(R.string.del_send_messag)
                .setPositiveButton(R.string.del_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 用户点击OK，执行删除操作
                        DBManager.getInstance().getDaoSession().getChatLogDao().deleteAll();
                        messagesAdapter.clear();
                    }
                })
                .setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 用户点击取消，不执行删除操作
                        dialog.dismiss();
                    }
                })
                .show();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                //弹出确认删除对话框，点ok再删除
                showDeleteConfirmationDialog();
                break;
            case R.id.action_copy:
                messagesAdapter.copySelectedMessagesText(this, getMessageStringFormatter(), true);
                ToastUtil.showLong(R.string.copied_message);
                break;
            case R.id.action_chat_session:
                createNewChatSession();
                break;
            case R.id.action_chat_sessions:
                ChatSessionFragment.startFragment(this);
                break;
            case R.id.sound_switch:
                boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false);
                if(needTts){
                    item.setIcon(R.drawable.song_not_play_icon);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false);
                }else{
                    item.setIcon(R.drawable.song_play_icon);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, true);
                }
                break;
        }
        return true;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ChatSessionEvent event) {
        PreferenceUtils.setSettingLong(this, Constants.CHAT_SESSION, event.id);
        sessionId = event.id;
        setting = event.settingName;
        messagesAdapter.clear(true);
        loadMessages();
        LogUtil.i("onEvent sessionChange loadMessages settingName="+setting);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ChatItemDelEvent event) {
        messagesAdapter.delete(event.message);
        ToastUtil.showShort(R.string.del_message);
        List<ChatLog> chatLogs = DBManager.getInstance().getDaoSession().getChatLogDao().queryRaw("where CHAT_ID=?",event.message.getId());
        DBManager.getInstance().getDaoSession().getChatLogDao().deleteInTx(chatLogs);
        LogUtil.i("onEvent delete ="+setting);
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
        btnOk.setOnClickListener(v -> {
            if (nameView.getText().toString().isEmpty()) {
                LogUtil.i("没有填名称");
                return;
            }
            ChatSessionLog log = new ChatSessionLog();
            try {
                log.setName(nameView.getText().toString());
                log.setCreateTime(new Date());
                log.setSetting("");
                try {
                    DBManager.getInstance().getDaoSession().getChatSessionLogDao().insert(log);
                } catch (Exception e) {
                    LogUtil.e(e);
                }
                onEvent(new ChatSessionEvent(log.id, log.setting));
                ToastUtil.showShort(R.string.save_ok);
                LogUtil.i("对话名称：" + nameView.getText().toString() + "---" + log.id);
            }catch (Exception e){
                LogUtil.e("log="+log,e);
            }
            popupWindow.dismiss();
        });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layout_messages);
        EventBusUtil.getEventBus().register(this);
        LogUtil.i("isDebug=" + MyApplication.isDebug);
        EventBusUtil.getEventBus().register(jump);
        if(MyApplication.isDebug){
            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN, false, AdsContext.AdsFrom.ADMOB, 1);
        }
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
        input.setAudioFinishListener((audioPath, time) -> {
            LogUtil.i("录音结束回调 ；"+audioPath);
            File file = new File(audioPath);
            if (file.exists()) {
                sendAudioMessage(audioPath, time);
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
        input.setPhoneClickListener(new InputRecordView.PhoneClickListener() {
            @Override
            public void click() {
                ToastUtil.showShort(R.string.talktome_unavailable);
            }

            @Override
            public void help() {
                //
                Map<String, Object> param = new HashMap<>();
                param.put(CommonConstants.ADS_SHOW_CONFIG, false);
                param.put(CommonConstants.ADS_POP_SHOW_CONFIG, false);
                LogUtil.i("helpurl = "+Constants.helpUrl);
                EventBusUtil.getEventBus().post(new ConfigActionEvent(CustomChatMessagesActivity.this, Constants.helpUrl, "Help", param));
            }
        });
        initChatSession();
        AdsContext.showNext(MyApplication.getInstance());
    }
    public void initChatSession(){
        GsonInfoListRequest request = new GsonInfoListRequest(this, Constants.getUrl(Constants.CHAT_SESSION_URL), ChatSessionLog.class, null, new Response.Listener<InfoListVo<ChatSessionLog>>(){
            @Override
            public void onResponse(InfoListVo<ChatSessionLog> chatSessionLogInfoListVo) {
                LogUtil.i("session list = "+GsonUtils.getInstance().toJson(chatSessionLogInfoListVo));
                if(chatSessionLogInfoListVo!=null && chatSessionLogInfoListVo.voList!=null){
                    DBManager.getInstance().getDaoSession().getChatSessionLogDao().insertOrReplaceInTx(chatSessionLogInfoListVo.voList);
                }
            }
        }, new CommonResponse.ResponseErrorListener());
        request.setTag("initChatSession");
        VolleyUtils.addRequest(request);
    }

    private void initAdapter() {
        messagesAdapter = new MyMessagesListAdapter(senderId);
        messagesAdapter.setLoadMoreListener(this);
        messagesList.setAdapter(messagesAdapter);
        messagesList.setOnTouchListener((v, event) -> {
            input.closeKeypad();
            return false;
        });
        loadMessages();

    }

    public void loadMessages() {
        sessionId = PreferenceUtils.getPrefLong(this, Constants.CHAT_SESSION, 0);
        ChatSessionLog chatSessionLog = DBManager.getInstance().getDaoSession().getChatSessionLogDao().load(sessionId);
        LogUtil.i("chatSessionLog = "+GsonUtils.getInstance().toJson(chatSessionLog));
        if(chatSessionLog!=null){
            setting = chatSessionLog.getSetting();
        }
        List<ChatLog> chatlogs = DBManager.getInstance().getDaoSession().getChatLogDao().queryRaw(" where SESSION_ID=" + sessionId);
        LogUtil.i("loadMessages sessionId=" + sessionId+"; chatlogs size="+chatlogs.size());


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
                    holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? User.gpt : User.my, msgContent.getOrg(), log.getCreateTime());
                    holdMsg.setImage(new Message.Image(msgContent.getContent()));
                }
                if (log.msgType == MessageType.FILE_TYPE) {
                    holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? User.gpt : User.my, msgContent.getOrg(), log.getCreateTime());
                    holdMsg.setLocalFile(new Message.LocalFile(msgContent.getContent(), msgContent.getFileName(), msgContent.getFileSize()));
                }
                holdMsg.setMsgType(log.getMsgType());
                messagesAdapter.addToStart(holdMsg, true);
            }
        }
        ChatSessionLog sessionLog = DBManager.getInstance().getDaoSession().getChatSessionLogDao().load(sessionId);
        if (sessionLog != null){
            sessionName = sessionLog.name;
            setToolbarTitle(sessionLog.getName(), false);
        }
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
//        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    private void takePersistentPermission(Uri uri) {
        try {
            // 申请长期读取权限
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException e) {
            LogUtil.e( "Failed to take persistent permission", e);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtil.i("onActivityResult resultCode="+resultCode+",requestCode="+requestCode+",data="+data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case InputRecordView.REQUEST_CODE_FILE:
//                    String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
//                    LogUtil.d("获取到的文件路径:"+filePath);
//                    sendFileMessage(mSenderId, mTargetId, filePath);
                    LogUtil.d("获取到的文件路径:"+data.getData());
                    if (data != null) {
                        Uri uri = data.getData();
                        // 1. 获取长期权限（可选）
                        takePersistentPermission(uri);
                        sendFileMessage(uri);
                    }
                    break;
                case InputRecordView.REQUEST_CODE_IMAGE:
                    // 图片选择结果回调
                    List<LocalMedia> selectListPic = PictureSelector.obtainSelectorList(data);
                    for (LocalMedia media : selectListPic) {
                        LogUtil.i("获取图片路径成功:" + media.getRealPath());
                        sendImageMessage(media);
                    }
                    break;
                case InputRecordView.REQUEST_CODE_QUSTION:
                    // 图片选择结果回调
                    List<LocalMedia> piss = PictureSelector.obtainSelectorList(data);
                    for (LocalMedia media : piss) {
                        LogUtil.i("获取图片路径成功:" + media.getRealPath());
                        sendQustionMessage(media);
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
            Message holdMsgTmp = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            initHoldMsg(holdMsgTmp);
            messagesAdapter.addToStart(holdMsgTmp, true);
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setText(hello);
            chatHistory.setSessinId(sessionId);
            chatHistory.setId(holdMsgTmp.getId());
            chatHistory.setSettingName(setting);
            chatHistory.setCharater(content);
            baseService.postData(String.format(Constants.getUrl(Constants.CHAT_URL)), chatHistory, listener, new CommonResponse.ResponseErrorListener(), TAG, Choice.class);
            AdsContext.showRand(this);
        }
    }


    //图片消息
    private void sendImageMessage(final LocalMedia media) {
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        try {
            MsgContent msgContent = new MsgContent();
            msgContent.setContent(media.getRealPath());
            Message myMsg = new Message(MessagesFixtures.getRandomId(), User.my, "");
            myMsg.setMsgType(MessageType.IMAGE_TYPE);
            myMsg.setImage(new Message.Image(media.getRealPath()));
            messagesAdapter.addToStart(myMsg, true);
            LogUtil.i("发送消息： " + media.getRealPath());
            Message holdMsgTmp = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            initHoldMsg(holdMsgTmp);
            messagesAdapter.addToStart(holdMsgTmp, true);
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.msgType = MessageType.IMAGE_TYPE;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setSessinId(sessionId);
            chatHistory.setId(holdMsgTmp.getId());
            LogUtil.i("站位id"+chatHistory.getId());
            ImagePhotoLoad.covertImage(this, media.getRealPath(), new ImagePhotoLoad.ImageConversionCallback() {
                @Override
                public void onConversionSuccess(File outputFilePath) {
                    baseService.postData(String.format(Constants.getUrl(Constants.CHAT_IMAGE_URL)), chatHistory, Arrays.asList(outputFilePath), listener,new CommonResponse.ResponseErrorListener(), "image",TAG, Choice.class);
                }
                @Override
                public void onConversionFailed(Exception e) {

                }
            });
        }catch (Exception e){
            LogUtil.e(e);
        }

    }
    //图片消息
    private void sendQustionMessage(final LocalMedia media) {
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        try {
            MsgContent msgContent = new MsgContent();
            msgContent.setContent(media.getRealPath());
            Message myMsg = new Message(MessagesFixtures.getRandomId(), User.my, "");
            myMsg.setMsgType(MessageType.IMAGE_TYPE);
            myMsg.setImage(new Message.Image(media.getRealPath()));
            messagesAdapter.addToStart(myMsg, true);
            LogUtil.i("发送消息： " + media.getRealPath());
            Message holdMsgTmp = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            initHoldMsg(holdMsgTmp);
            messagesAdapter.addToStart(holdMsgTmp, true);
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.msgType = MessageType.IMAGE_TYPE;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setSessinId(sessionId);
            chatHistory.setId(holdMsgTmp.getId());
            LogUtil.i("站位id"+chatHistory.getId());
            ImagePhotoLoad.covertImage(this, media.getRealPath(), new ImagePhotoLoad.ImageConversionCallback() {
                @Override
                public void onConversionSuccess(File outputFilePath) {
                    baseService.postData(String.format(Constants.getUrl(Constants.CHAT_QUESTION_URL)), chatHistory, Arrays.asList(outputFilePath), listener,new CommonResponse.ResponseErrorListener(), "image",TAG, Choice.class);
                }
                @Override
                public void onConversionFailed(Exception e) {

                }
            });
        }catch (Exception e){
            LogUtil.e(e);
        }

    }

    //视频消息
    private void sendVideoMessage(final LocalMedia media) {
    }

    //文件消息
    private void sendFileMessage(Uri uri) {
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        try {
            FileUtils.FileInfo fileInfo = FileUtils.getFileFromUri(uri, this);

            MsgContent msgContent = new MsgContent();
            msgContent.setContent(uri.toString());
            msgContent.setFileName(fileInfo.fileName);
            msgContent.setFileSize(fileInfo.fileSize);
            Message myMsg = new Message(MessagesFixtures.getRandomId(), User.my, "");
            myMsg.setMsgType(MessageType.FILE_TYPE);
            myMsg.setText(fileInfo.fileName);
            myMsg.setLocalFile(new Message.LocalFile(uri.toString(),fileInfo.fileName, fileInfo.fileSize));
            messagesAdapter.addToStart(myMsg, true);
            LogUtil.i("发送消息： " + uri.toString());
            Message holdMsgTmp = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            initHoldMsg(holdMsgTmp);
            messagesAdapter.addToStart(holdMsgTmp, true);
            ChatLog relog = new ChatLog();
            relog.content = GsonUtils.getInstance().toJson(msgContent);
            relog.who = ChatMessageBean.TYPE_SEND;
            relog.msgType = MessageType.FILE_TYPE;
            relog.setChatId(myMsg.getId());
            relog.createTime = new Date();
            relog.sessionId = sessionId;
            DBManager.getInstance().saveChatLog(relog);
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setSessinId(sessionId);
            chatHistory.setId(holdMsgTmp.getId());
            LogUtil.i("站位id"+chatHistory.getId());

            baseService.postData(String.format(Constants.getUrl(Constants.CHAT_FILE_URL)), chatHistory, Arrays.asList(fileInfo.file), listener,new CommonResponse.ResponseErrorListener(), "file",TAG, Choice.class);
        }catch (Exception e){
            LogUtil.e(e);
        }
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

            Message holdMsgTmp = new Message(MessagesFixtures.getRandomId(), User.gpt, getString(R.string.hold_please));
            initHoldMsg(holdMsgTmp);
            messagesAdapter.addToStart(holdMsgTmp, true);

            MsgContent msgContent = new MsgContent();
            msgContent.setContent(path);
            msgContent.setOrg(asr);
            msgContent.setTime(time);
            msgContent.setMsgId(myMsg.getId());
            msgContent.setHoldId(holdMsgTmp.getId());
            msgContents.put(msgContent.getMsgId(), msgContent);
            AsrContentForm asrContentForm = new AsrContentForm();
            byte[] voice = FileUtils.readFile(wavPath);
            LogUtil.i("voice size:"+voice.length);
            asrContentForm.setContent(Base64Util.encodeBase64(voice));
            asrContentForm.setId(msgContent.getMsgId());
            baseService.postData(Constants.getUrl(Constants.CHAT_ASR_URL),asrContentForm,asrListener, new CommonResponse.ResponseErrorListener(), ASRTAG, AsrResponseVo.class);

//            recognizeFile(wavPath, msgContent);
        }
    }

    public void initHoldMsg(Message msg){
    }
    public void initLLMMsg(ChatLog msg, MsgContent msgContent){
    }
    public void llmCallBack(Choice choice){}

    @Override
    public void onLoadMore(int page, int totalItemsCount) {

    }
}
