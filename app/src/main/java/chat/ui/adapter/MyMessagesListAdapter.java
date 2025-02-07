package chat.ui.adapter;

import android.net.Uri;

import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import java.io.File;

import chat.ui.adapter.media.holders.MyIncomingVoiceMessageViewHolder;
import chat.ui.adapter.media.holders.MyInComingMassageHandler;
import chat.ui.adapter.media.holders.MyOutComingImageMessageViewHolder;
import chat.ui.adapter.media.holders.MyOutComingMassageHandler;
import chat.ui.adapter.media.holders.MyOutComingVoiceMessageViewHolder;
import chat.ui.data.model.Message;
import tz.co.hosannahighertech.messagekit.commons.ImageLoader;
import tz.co.hosannahighertech.messagekit.commons.ViewHolder;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

public class MyMessagesListAdapter extends MessagesListAdapter<Message> implements MessageHolders.ContentChecker<Message> {
    public static final byte CONTENT_TYPE_VOICE = 1;
    private static MessageHolders holdersConfig = new MessageHolders()
            .setIncomingTextConfig(MyInComingMassageHandler.class, R.layout.item_custom_incoming_text_message_new)
            .setIncomingImageLayout(R.layout.item_custom_incoming_image_message)
            .setOutcomingImageConfig(MyOutComingImageMessageViewHolder.class, R.layout.item_custom_outcoming_image_message_new)
            .setOutcomingTextConfig(MyOutComingMassageHandler.class, R.layout.item_custom_outcoming_text_message_new);
    protected static ImageLoader imageLoader = (imageView, url, payload) -> {
        File file = new File(url);
        Uri uri = Uri.fromFile(file);

        ImagePhotoLoad.loadCommonImg(MyApplication.getInstance(),uri ,imageView);
    };

    public MyMessagesListAdapter(String senderId) {
        super(senderId, holdersConfig, imageLoader);
        holdersConfig.registerContentType(
                CONTENT_TYPE_VOICE,
                MyIncomingVoiceMessageViewHolder.class,
                R.layout.item_custom_incoming_voice_message_new,
                MyOutComingVoiceMessageViewHolder.class,
                R.layout.item_custom_outcoming_voice_message_new,
                this);
    }

    @Override
    public boolean hasContentFor(Message message, byte type) {
        if (type == CONTENT_TYPE_VOICE) {
            return message.getVoice() != null;
//            return message.getVoice() != null
//                    && message.getVoice().getUrl() != null
//                    && !message.getVoice().getUrl().isEmpty();
        }
        return false;
    }
    public int getMessagePositionById(String id) {
        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.item instanceof IMessage) {
                Message message = (Message) wrapper.item;
                if (message.getId().contentEquals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
    }
}
