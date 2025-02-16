package chat.ui;

import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;

import com.android.volley.VolleyError;
import com.openai.CommonConstants;
import com.openai.commonutil.Base64Util;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.PermissionHelper;
import com.openai.commonview.view.widget.MediaManager;
import com.openai.commonutil.FileUtils;
import com.openai.commonutil.GsonUtils;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.StringUtils;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openai.net.request.CommonResponse;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.AsrContentForm;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.AsrResponseVo;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.bean.vo.TtsResponseVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.config.TtsLoadEvent;
import com.openapi.ks.myapp.view.MyIncomingAudioFooterView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import chat.ui.data.model.ChatHistory;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;

public class CustomChatMessagesWithAsrActivity extends CustomChatMessagesActivity {

    private static String TTSTAG = "TTSTAG";
    public Map<String,Message> holdMsgs = new HashMap<>();
    private  Map<String,Function> callbacks = new HashMap<>();
    CommonResponse.ResponseOkListener ttsListener = new CommonResponse.ResponseOkListener<TtsResponseVo>() {
        @Override
        public void onResponse(TtsResponseVo vo) {
                String fileName = vo.getFileName();
                String abFileName = FileUtils.getWriteFilePath(MyApplication.getInstance())+"/"+fileName;
                try {
                    Files.write(Paths.get(abFileName), Base64Util.decodeBase64(vo.getData())); // 写入数据
                } catch (IOException e) {
                   LogUtil.e(e);
                }

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
                Message holdMsg = holdMsgs.get(vo.getId());
                if(holdMsg == null){
                    LogUtil.i("holdMsg is null"+vo.getId());
                    return;
                }
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
                messagesAdapter.update(holdMsg);
                if(callbacks.get(vo.getId())!=null){
                    callbacks.get(vo.getId()).apply(holdMsg);
                }else{
                        LogUtil.i("callback is null");
                    }
                }
        };

    @Override
    public void llmCallBack(Choice choice) {
        super.llmCallBack(choice);
        LogUtil.i("开始tts   "+choice.getMessage());
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
    @Override
    public void initHoldMsg(Message msg){
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false);
        if(needTts) {
            msg.setMsgType(MessageType.AUDIO_TYPE);
            msg.setVoice(new Message.Voice(null, 0));
        }
    }

    @Override
    public void initLLMMsg(ChatLog msg, MsgContent msgContent) {
        super.initLLMMsg(msg, msgContent);
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false);
        if(needTts) {
            msg.setMsgType(MessageType.AUDIO_TYPE);
            msgContent.setOrg(msgContent.getContent());
            msgContent.setContent("");
            msgContent.setTime(0);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void ttsLoad(TtsLoadEvent ttsLoadEvent) {
        callbacks.put(ttsLoadEvent.message.getId(),ttsLoadEvent.function);
        LogUtil.i("ttsLoad:"+ttsLoadEvent.message.getText());
        if(!StringUtils.isEmpty(ttsLoadEvent.message.getText())) {
            AsrContentForm asrContentForm = new AsrContentForm();
            asrContentForm.setId(ttsLoadEvent.message.getId());
            asrContentForm.setContent(ttsLoadEvent.message.getText());
            holdMsgs.put(ttsLoadEvent.message.getId(), ttsLoadEvent.message);
            baseService.postData(Constants.getUrl(Constants.CHAT_TTS_URL), asrContentForm, ttsListener, new CommonResponse.ResponseErrorListener(){
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    super.onErrorResponse(volleyError);
                    if(callbacks.get(asrContentForm.id)!=null){
                        callbacks.get(asrContentForm.id).apply(null);
                    };
                }

                @Override
                protected void onError() {
                    super.onError();
                    if(callbacks.get(asrContentForm.id)!=null){
                        callbacks.get(asrContentForm.id).apply(null);
                    };                }
            }, TTSTAG, TtsResponseVo.class);
        }
    }
}
