package com.livekit.voiceassistant_old;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.livekit.voiceassistant.LiveKitFragmentManager;

/**
 * 旧版Java使用示例
 * 展示如何在Java代码中使用旧版语音助手组件
 */
public class JavaUsageExample extends AppCompatActivity {
    
    private static final String URL = "url";
    private static final String TOKEN = "token";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 示例URL和Token
        String url = "wss://your-server.livekit.cloud";
        String token = "your-jwt-token";
        
        // 启动旧版语音助手Fragment
        androidx.fragment.app.Fragment fragment = LiveKitFragmentManager.Companion.newOldVoiceAgentInstance(url, token);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }
    
    /**
     * 启动旧版语音助手
     */
    public void startOldVoiceAssistant(String url, String token) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(URL, url);
        intent.putExtra(TOKEN, token);
        startActivity(intent);
    }
    
    /**
     * 显示旧版主Fragment
     */
    public void showOldMainFragment(String url, String token) {
        androidx.fragment.app.Fragment fragment = LiveKitFragmentManager.Companion.newOldMainInstance(url, token);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }
    
    /**
     * 使用FragmentManager切换Fragment
     */
    public void switchToOldVoiceAgent(String url, String token) {
        // 使用LiveKitFragmentManager切换Fragment
        androidx.fragment.app.Fragment fragment = LiveKitFragmentManager.Companion.newOldVoiceAgentInstance(url, token);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack("old_voice_agent")
                .commit();
    }
}
