package com.livekit.voiceassistant_old

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager as AndroidFragmentManager

/**
 * 旧版Fragment管理器
 * 管理旧版语音助手相关的Fragment
 */
object FragmentManager {
    
    /**
     * 显示旧版语音助手Fragment
     */
    fun showOldVoiceAgentFragment(
        fragmentManager: AndroidFragmentManager,
        containerId: Int,
        url: String,
        token: String
    ) {
        val fragment = LivekitVoiceAgentFragment.newInstance(url, token)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack("old_voice_agent")
            .commit()
    }
    
    /**
     * 检查是否正在显示旧版语音助手Fragment
     */
    fun isShowingOldVoiceAgentFragment(fragmentManager: AndroidFragmentManager): Boolean {
        val currentFragment = fragmentManager.findFragmentById(android.R.id.content)
        return currentFragment is LivekitVoiceAgentFragment
    }
    
    /**
     * 返回上一个Fragment
     */
    fun goBack(fragmentManager: AndroidFragmentManager) {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
    }
    
    /**
     * 清除所有Fragment
     */
    fun clearAllFragments(fragmentManager: AndroidFragmentManager) {
        fragmentManager.popBackStack(null, AndroidFragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
}
