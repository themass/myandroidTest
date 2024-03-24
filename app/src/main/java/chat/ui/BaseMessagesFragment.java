package chat.ui;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import chat.ui.data.model.Message;
import chat.ui.utils.AppUtils;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

/*
 * Created by troy379 on 04.04.17.
 */
public abstract class BaseMessagesFragment extends BaseChatPullLoadbleFragment
        implements MessagesListAdapter.SelectionListener,
        MessagesListAdapter.OnLoadMoreListener, OnBackKeyDownListener {

    private static final int TOTAL_MESSAGES_COUNT = 100;

    protected final String senderId = "0";

    protected MessagesListAdapter<Message> messagesAdapter;

    private Menu menu;
    private int selectionCount;
    private Date lastLoadedDate;


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
        getActivity().getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        onSelectionChanged(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                messagesAdapter.deleteSelectedMessages();
                break;
            case R.id.action_copy:
                messagesAdapter.copySelectedMessagesText(getActivity(), getMessageStringFormatter(), true);
                AppUtils.showToast(getActivity(), R.string.copied_message, true);
                break;
        }
        return true;
    }

    @Override
    public boolean onkeyBackDown() {
        if (selectionCount != 0) {
            messagesAdapter.unselectAllItems();
        }
        return true;
    }

    @Override
    public void onSelectionChanged(int count) {
        this.selectionCount = count;
        menu.findItem(R.id.action_delete).setVisible(count > 0);
        menu.findItem(R.id.action_copy).setVisible(count > 0);
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        return null;
    }
    private MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
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
