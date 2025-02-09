package chat.ui;

import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;

import com.openai.commonutil.Base64Util;
import com.openai.commonutil.PermissionHelper;
import com.openai.commonview.view.widget.MediaManager;
import com.openai.asr.AsrByteDance;
import com.openai.asr.AsrConstants;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import chat.ui.data.model.ChatHistory;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;

public class CustomChatMessagesWithAsrActivity extends CustomChatMessagesActivity {

    private static String TTSTAG = "TTSTAG";
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
            }
        };

    @Override
    public void llmCallBack(Choice choice) {
        super.llmCallBack(choice);
        LogUtil.i("开始tts   "+choice.getMessage());
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), AsrConstants.TTS_OPEN, false);
        try {
            if (needTts) {
                AsrContentForm asrContentForm = new AsrContentForm();
                asrContentForm.setId(choice.getId());
                asrContentForm.setContent(choice.getMessage().getContent());
                baseService.postData(Constants.getUrl(Constants.CHAT_TTS_URL),asrContentForm,ttsListener, new CommonResponse.ResponseErrorListener(), TTSTAG, TtsResponseVo.class);
            }
        }catch (Exception e){
            LogUtil.e(e);
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
    @Override
    public void initHoldMsg(Message msg){
        boolean needTts = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), AsrConstants.TTS_OPEN, false);
        if(needTts) {
            msg.setMsgType(MessageType.AUDIO_TYPE);
            msg.setVoice(new Message.Voice(null, 0));
        }
    }
}
