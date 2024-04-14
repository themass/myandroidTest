package chat.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;


import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.chatfree.R;
import com.openapi.ks.myapp.bean.form.ChatLog;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.ui.base.app.BaseDrawerActivity;
import com.openapi.ks.myapp.ui.base.app.BaseToolBarActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import chat.asrassistant.model.ChatMessageBean;
import chat.ui.data.fixtures.MessagesFixtures;
import chat.ui.data.model.Message;
import chat.ui.data.model.User;
import chat.ui.utils.AppUtils;
import tz.co.hosannahighertech.messagekit.commons.ImageLoader;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

/*
 * Created by troy379 on 04.04.17.
 */
public abstract class BaseChatMessagesActivity extends BaseDrawerActivity
        implements MessagesListAdapter.SelectionListener,
        MessagesListAdapter.OnLoadMoreListener {

    private static final int TOTAL_MESSAGES_COUNT = 100;

    protected final String senderId = "0";
    protected ImageLoader imageLoader;
    protected MessagesListAdapter<Message> messagesAdapter;
    public Long sessionId = 0l;
    private Menu menu;
    public int selectionCount;
    User my = new User( "0", MessagesFixtures.names.get(0), "", true);
    User gpt = new User( "110",MessagesFixtures.names.get(0), "http://test", true);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageLoader = (imageView, url, payload) -> {
            imageView.setImageResource(R.drawable.ai_chat);
        };

    }
    @Override
    public void setupView() {
        super.setupView();
        showToolbar(true);
    }

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
                AppUtils.showToast(this, R.string.copied_message, true);
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
    private void createNewChatSession(){
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
        popupWindow.showAtLocation(BaseChatMessagesActivity.this.getWindow().getDecorView(),Gravity.CENTER,0,0);
//        popupWindow.showAtLocation(findViewById(R.id.fl_body), Gravity.CENTER, screenWidth / 2, screenHeight / 2);
        EditText nameView =  contentView.findViewById(R.id.chat_name);
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
                if(nameView.getText().toString().isEmpty()){
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
                LogUtil.i("对话名称："+nameView.getText().toString()+"---"+log.id);
                popupWindow.dismiss();
            }
        });
    }
    @Override
    public void onLoadMore(int page, int totalItemsCount) {
    }

    @Override
    public void onSelectionChanged(int count) {
        this.selectionCount = count;
        menu.findItem(R.id.action_delete).setVisible(count > 0);
        menu.findItem(R.id.action_copy).setVisible(count > 0);
    }

    public void loadMessages() {
        sessionId =  PreferenceUtils.getPrefLong(this, Constants.CHAT_SESSION, 0);
        LogUtil.i("loadMessages sessionId="+sessionId);
        List<ChatLog> chatlogs = DBManager.getInstance().getDaoSession().getChatLogDao().queryRaw(" where SESSION_ID="+sessionId);
        for (ChatLog log:chatlogs){
            if(log.getWho() != ChatMessageBean.TYPE_SYSTEM) {
                Message holdMsg = new Message(log.getChatId(), log.getWho() == ChatMessageBean.TYPE_RECEIVED ? gpt :  my, log.content, log.getCreateTime());
                messagesAdapter.addToStart(holdMsg, true);
            }
        }
        ChatSessionLog sessionLog = DBManager.getInstance().getDaoSession().getChatSessionLogDao().load(sessionId);
        if(sessionLog!=null)
            setToolbarTitle(sessionLog.getName(),false);
    }

    protected MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
        return message -> {
            String createdAt = new SimpleDateFormat("MMM d, EEE 'at' h:mm a", Locale.getDefault())
                    .format(message.getCreatedAt());

            String text = message.getText();
            if (text == null) text = "[attachment]";

            return String.format(Locale.getDefault(), "%s: %s (%s)",
                    message.getUser().getName(), text, createdAt);
        };
    }
}
