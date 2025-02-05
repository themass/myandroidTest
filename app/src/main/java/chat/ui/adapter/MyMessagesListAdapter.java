package chat.ui.adapter;

import android.view.View;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;

import chat.ui.adapter.media.holders.IncomingVoiceMessageViewHolder;
import chat.ui.adapter.media.holders.MyInComingMassageHandler;
import chat.ui.adapter.media.holders.MyOutComingMassageHandler;
import chat.ui.adapter.media.holders.OutComingVoiceMessageViewHolder;
import chat.ui.data.model.Message;
import chat.ui.data.model.User;
import tz.co.hosannahighertech.messagekit.commons.ImageLoader;
import tz.co.hosannahighertech.messagekit.commons.models.IMessage;
import tz.co.hosannahighertech.messagekit.messages.MessageHolders;
import tz.co.hosannahighertech.messagekit.messages.MessagesListAdapter;

public class MyMessagesListAdapter extends MessagesListAdapter<Message> implements MessageHolders.ContentChecker<Message> {
    public static final byte CONTENT_TYPE_VOICE = 1;
    private static MessageHolders holdersConfig = new MessageHolders()
            .setIncomingTextConfig(MyInComingMassageHandler.class,R.layout.item_custom_incoming_text_message_new)
            .setIncomingImageLayout(R.layout.item_custom_incoming_image_message)
            .setOutcomingImageLayout(R.layout.item_custom_outcoming_image_message)
            .setOutcomingTextConfig(MyOutComingMassageHandler.class, R.layout.item_custom_outcoming_text_message_new);
    protected static ImageLoader imageLoader = (imageView, url, payload) -> {
        imageView.setImageResource(R.drawable.ai_chat);
    };

    public MyMessagesListAdapter(String senderId) {
        super(senderId, holdersConfig, imageLoader);
        holdersConfig.registerContentType(
                CONTENT_TYPE_VOICE,
                IncomingVoiceMessageViewHolder.class,
                R.layout.item_custom_incoming_voice_message_new,
                OutComingVoiceMessageViewHolder.class,
                R.layout.item_custom_outcoming_voice_message_new,
                this);
//        setOnMessageViewClickListener(this);
//        setOnMessageViewLongClickListener(this);
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

//    @Override
//    public void onMessageViewClick(View view, IMessage message) {
//        Message msg = (Message) message;
//        if(msg.getUser() == User.my){
//            if (msg.getVoice() != null) {
//                OutComingVoiceMessageViewHolder.click(view, msg);
//                OutComingVoiceMessageViewHolder.onLongClick(view, msg);
//            } else if (msg.getImage() != null) {
//
//            }
//        }if(msg.getUser() == User.gpt){
//            if (msg.getVoice() != null) {
//                IncomingVoiceMessageViewHolder.click(view, msg);
//                IncomingVoiceMessageViewHolder.onLongClick(view, msg);
//            } else if (msg.getImage() != null) {
//
//            }
//        }

//    }
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

//    @Override
//    public void onMessageViewLongClick(View view, IMessage message) {
//        LogUtil.i("onMessageViewLongClick:"+message);
////        Message msg = (Message) message;
////        if (msg.getVoice() != null) {
////            OutcomingVoiceMessageViewHolder.onLongClick(view, msg);
////        } else if (msg.getImage() != null) {
////
////        }
//    }
}
