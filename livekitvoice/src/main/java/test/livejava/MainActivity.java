package test.livejava;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import io.livekit.android.example.voiceassistant.R;

public class MainActivity extends AppCompatActivity {
    private ChatLogView chatLogView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatLogView = findViewById(R.id.chatLogView);

        // 添加消息
        chatLogView.addMessage(new Message("Hello, how can I help you?", false));
        chatLogView.addMessage(new Message("I need some information.", true));
    }
}