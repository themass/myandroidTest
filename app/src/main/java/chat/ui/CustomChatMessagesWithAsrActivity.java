package chat.ui;

import static com.luck.picture.lib.permissions.PermissionConfig.WRITE_EXTERNAL_STORAGE;
import static com.openapi.commons.common.util.PermissionHelper.CAMERA;
import static com.openapi.commons.common.util.PermissionHelper.RECORD_AUDIO;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.openapi.commons.common.asr.AsrByteDance;
import com.openapi.commons.common.util.GsonUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PermissionHelper;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.chatfree.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.vo.Choice;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.config.PermEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import chat.ui.data.model.ChatHistory;
import chat.ui.data.model.ChatMessageBean;
import chat.ui.data.model.Message;
import chat.ui.data.model.MessageType;
import chat.ui.data.model.MsgContent;
import chat.ui.data.model.SimpleMessage;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

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
            asrByteDance.initSendEngine(this);
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

}
