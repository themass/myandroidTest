package com.livekit.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import io.livekit.android.LiveKit
import io.livekit.android.util.LoggingLevel

/**
 * 主 Activity，作为 Fragment 的容器
 * 现在使用 Fragment 来承载 UI 逻辑，Activity 只负责管理 Fragment
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG

        // 设置布局文件，包含一个 Fragment 容器
        setContentView(R.layout.activity_main)
        
        // 如果 savedInstanceState 为 null，说明是第一次创建，添加主 Fragment
        if (savedInstanceState == null) {
            // 默认显示新的 Compose 样式的 Fragment
            LiveKitFragmentManager.showMainFragment(
                activity = this,
                containerId = R.id.fragment_container,
                addToBackStack = false
            )
        }
    }
    
    /**
     * 切换 Fragment 样式的方法
     * 可以在运行时切换新旧样式
     */
    fun switchToNewStyle() {
        LiveKitFragmentManager.showMainFragment(
            activity = this,
            containerId = R.id.fragment_container,
            addToBackStack = true
        )
    }
    
    /**
     * 切换到旧样式的方法
     */
    fun switchToOldStyle(url: String, token: String) {
        LiveKitFragmentManager.showOldFragment(
            activity = this,
            containerId = R.id.fragment_container,
            url = url,
            token = token,
            addToBackStack = true
        )
    }
    
    /**
     * 获取当前 Fragment 管理器
     */
    fun getLiveKitFragmentManager(): LiveKitFragmentManager.Companion {
        return LiveKitFragmentManager
    }
}
