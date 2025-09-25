package com.livekit.voiceassistant_old

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.livekit.voiceassistant.LiveKitFragmentManager

/**
 * 旧版使用示例Activity
 * 展示如何使用旧版语音助手组件
 */
class ExampleUsageActivity : AppCompatActivity() {
    
    companion object {
        const val URL = "url"
        const val TOKEN = "token"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 示例URL和Token
        val url = "wss://your-server.livekit.cloud"
        val token = "your-jwt-token"
        
        // 启动旧版语音助手Fragment
        val fragment = LiveKitFragmentManager.newOldVoiceAgentInstance(url, token)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit()
    }
    
    /**
     * 启动旧版语音助手
     */
    fun startOldVoiceAssistant(url: String, token: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(URL, url)
            putExtra(TOKEN, token)
        }
        startActivity(intent)
    }
    
    /**
     * 显示旧版主Fragment
     */
    fun showOldMainFragment(url: String, token: String) {
        val fragment = MainFragment.newInstance(url, token)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit()
    }
}
