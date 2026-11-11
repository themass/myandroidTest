package com.openapi.app.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openapi.speech.audio.AudioPlayer
import com.openapi.app.home.presentation.HomeScreen
import com.openapi.uikit.theme.AppMaterialTheme

/**
 * 主页：底部三个标签（对话、频道、我的）。启动页直接进入这里。
 *
 * 对话标签用的就是原来单独的对话页所用的 ChatRoute，频道和「我的」承接了旧抽屉和旧首页的入口。
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppMaterialTheme { HomeScreen() }
        }
        if (savedInstanceState == null) {
            HomeDependencies.require().onHomeStarted(this)
        }
    }

    override fun onDestroy() {
        AudioPlayer.stop()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, HomeActivity::class.java))
        }
    }
}
