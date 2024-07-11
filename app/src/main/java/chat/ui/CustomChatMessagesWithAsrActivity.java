package chat.ui;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.hrl.chaui.widget.MediaManager;
import com.openapi.commons.common.asr.AsrByteDance;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

import chat.ui.data.model.ChatHistory;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;

public class CustomChatMessagesWithAsrActivity extends CustomChatMessagesWithPermActivity
        implements AsrByteDance.AsrCallBackListener {

    public static void open(Context context) {
        context.startActivity(new Intent(context, CustomChatMessagesWithAsrActivity.class));
    }
    private AsrByteDance asrByteDance;

    @Override
    public void onDestroy() {
        stopSpeed();
        super.onDestroy();
    }
//----------------------------------------------------------
    //----------------------------------------------------------------------
    @Override
    public void initVosk() {
        super.initVosk();
        if(mPermissionHelper.checkPermissions() && asrByteDance ==null) {
            asrByteDance = new AsrByteDance();
            asrByteDance.initEngine(this, this);
            asrByteDance.initAsrEngine(this);
            asrByteDance.initTtsEngine(this);
        }
    }

    private void stopSpeed() {
        if(asrByteDance != null) {
            asrByteDance.stopEngine();
        }
    }

    public void recognizeFile(String filePath, MsgContent msgContent) {
        if (asrByteDance == null) {
            ToastUtil.showLong(R.string.hold_please);
        }
        asrByteDance.sendEngine(filePath, msgContent);
    }
    @Override
    public void speechAsrResult(String data, boolean isFinish) {

        if (isFinish && !StringUtils.isEmpty(data)) {
            this.runOnUiThread(() -> {
                try {
                    // 从回调的 json 数据中解析 ASR 结果
                    JSONObject reader = new JSONObject(data);
                    if (!reader.has("result")) {
                        return;
                    }
                    String text = reader.getJSONArray("result").getJSONObject(0).getString("text");
                    if (text.isEmpty()) {
                        text = "......";
                    }
                    LogUtil.i("发送消息： " + text);
                    LogUtil.i("msgContents:"+msgContents);
                    MsgContent msgContent = msgContents.remove(0);
                    msgContent.setOrg(text);
                    Message sendMsg = messagesAdapter.getMessageByIndex(messagesAdapter.getMessagePositionById(msgContent.getMsgId()));
                    sendMsg.setText(text);
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

                    history.add(new SimpleMessage(text, "user"));
                    ChatHistory chatHistory = new ChatHistory();
                    chatHistory.setContent(GsonUtils.getInstance().toJson(history));
                    chatHistory.setId(msgContent.getHoldId());
                    chatHistory.setCharater(content);
                    baseService.postData(String.format(Constants.getUrl(Constants.CHAT_URL)), chatHistory, listener, new CommonResponse.ResponseErrorListener() {
                        @Override
                        protected void onError() {
                            super.onError();
                        }
                    }, TAG, Choice.class);
                } catch (JSONException e) {
                    LogUtil.e(e);
                }
            });

        }
    }

    @Override
    public void speechStart(String id) {

    }

    @Override
    public void speechStop() {

    }

    @Override
    public void callBackTtsData(String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 让当前线程睡眠 2 秒
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    LogUtil.e(e);
                }

                String fileName = "tts_"+name+".wav";
                String abFileName = FileUtils.getWriteFilePath(MyApplication.getInstance())+"/"+fileName;
                String abUrlFileName = "file://"+abFileName;
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                long time = 0;
                LogUtil.i("音频文件地址 = "+abFileName);
                try {
                    retriever.setDataSource(abFileName);
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    time = Long.parseLong(durationStr);
                    LogUtil.i("解析文件长度： "+durationStr);
                } catch (Exception e) {
                    LogUtil.e(e);
                } finally {
                    try {
                        retriever.release();
                    } catch (IOException e) {
                        LogUtil.e(e);
                    }
                }
                Message.Voice voice = new Message.Voice(abUrlFileName, (int) (time/1000));
                holdMsg.setVoice(voice);
                holdMsg.setMsgType(MessageType.AUDIO_TYPE);
                LogUtil.i("update msg to audio="+holdMsg.getId());
                //大模型返回的时候存储了这条消息，里面有chatID
                MsgContent msgContent = new MsgContent();
                msgContent.setContent(abUrlFileName);
                msgContent.setOrg(holdMsg.getText());
                msgContent.setTime((int) (time/1000));
                msgContent.setMsgId(relogLlm != null? relogLlm.getChatId():null);
                msgContent.setHoldId(holdMsg.getId());
                if(relogLlm != null){
                    relogLlm.msgType = MessageType.AUDIO_TYPE;
                    relogLlm.content = GsonUtils.getInstance().toJson(msgContent);
                    DBManager.getInstance().saveChatLog(relogLlm);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messagesAdapter.update(holdMsg);
                    }
                });
            }
        }).start();


    }

    @Override
    public void llmCallBack(String text) {
        super.llmCallBack(text);
        LogUtil.i("开始tts   "+text);
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.TTS_OPEN, false);
        if(needTts) {
            asrByteDance.sendTtsEngine(text);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // app 切到后台停止音频
        MediaManager.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MediaManager.resume();
    }
}
