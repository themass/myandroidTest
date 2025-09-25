package com.livekit.voiceassistant_old

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.livekit.voiceassistant.LiveKitFragmentManager

/**
 * 旧版主Activity - 语音助手入口
 * 负责启动和切换不同的Fragment
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        const val URL = "url"
        const val TOKEN = "token"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 获取传递的参数
        val url = intent.getStringExtra(URL)
        val token = intent.getStringExtra(TOKEN)
        
        // 启动旧版语音助手Fragment
        val fragment = LiveKitFragmentManager.newOldVoiceAgentInstance(url, token)
        showFragment(fragment)
    }
    
    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit()
    }
}
