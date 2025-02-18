package test.livejava;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

import io.livekit.android.example.voiceassistant.R;

public class ChatLogView extends RecyclerView {
    private ChatLogAdapter adapter;
    private List<Message> messages;

    public ChatLogView(Context context) {
        super(context);
        init(context);
    }

    public ChatLogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChatLogView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setLayoutManager(new LinearLayoutManager(context));
        messages = new ArrayList<>();
        adapter = new ChatLogAdapter(context, messages);
        setAdapter(adapter);
    }

    public void addMessage(Message message) {
        messages.add(message);
        adapter.notifyDataSetChanged();
        scrollToPosition(messages.size() - 1);
    }

    public void clearMessages() {
        messages.clear();
        adapter.notifyDataSetChanged();
    }

    private static class ChatLogAdapter extends RecyclerView.Adapter<ChatLogAdapter.MessageViewHolder> {
        private List<Message> messages;
        private Context context;

        public ChatLogAdapter(Context context, List<Message> messages) {
            this.context = context;
            this.messages = messages;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            Message message = messages.get(position);
            holder.bind(message);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class MessageViewHolder extends RecyclerView.ViewHolder {
            private TextView senderTextView;
            private TextView messageTextView;

            public MessageViewHolder(@NonNull View itemView) {
                super(itemView);
                senderTextView = itemView.findViewById(R.id.senderTextView);
                messageTextView = itemView.findViewById(R.id.messageTextView);
            }

            public void bind(Message message) {
                senderTextView.setText(message.isLocal ? "YOU" : "AI");
                messageTextView.setText(message.text);
                messageTextView.setGravity(message.isLocal ? android.view.Gravity.END : android.view.Gravity.START);
                messageTextView.setBackgroundColor(message.isLocal ? 0x330000FF : 0x33888888); // Blue for local, gray for others
            }
        }
    }
}