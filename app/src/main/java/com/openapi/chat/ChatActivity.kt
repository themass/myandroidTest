package com.openapi.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openapi.chat.presentation.ui.ChatRoute
import com.openapi.core.media.AudioPlayer

/**
 * 对话宿主页面。对外只暴露 [start]，app/抽屉/Fragment 都不需要知道内部实现。
 *
 * 界面装配在 [ChatRoute] 里，主页的对话 Tab 用的是同一个 composable，
 * 保证"从抽屉进来的对话"和"从主页 Tab 看到的对话"是同一套代码。
 */
class ChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChatRoute() }
    }

    override fun onDestroy() {
        AudioPlayer.stop()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, ChatActivity::class.java))
        }
    }
}
