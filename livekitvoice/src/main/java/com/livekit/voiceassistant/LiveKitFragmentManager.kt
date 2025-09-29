package com.livekit.voiceassistant

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.livekit.voiceassistant.fragment.SimpleFragment
import com.livekit.voiceassistant.fragment.CompleteFragment
import com.livekit.voiceassistant.fragment.WaveFragment
import com.livekit.voiceassistant.fragment.VoiceFragment

/**
 * LiveKit Fragment管理器 - 入口切换类
 * 负责管理不同版本的Fragment切换和token传递
 */
class LiveKitFragmentManager {
    
    companion object {
        /**
         * 创建SimpleFragment实例
         */
        fun newInstance(url: String?, token: String?): Fragment {
            return SimpleFragment.newInstance(
                url ?: "wss://your-server.livekit.cloud",
                token ?: "your-jwt-token"
            )
        }
        
        /**
         * 创建CompleteFragment实例
         */
        fun newCompleteInstance(url: String?, token: String?): Fragment {
            return CompleteFragment.newInstance(
                url ?: "wss://your-server.livekit.cloud", 
                token ?: "your-jwt-token"
            )
        }
        
        /**
         * 创建WaveFragment实例
         */
        fun newOriginalWaveInstance(url: String?, token: String?): Fragment {
            return WaveFragment.newInstance(
                url ?: "wss://your-server.livekit.cloud",
                token ?: "your-jwt-token"
            )
        }
        
        /**
         * 创建旧版VoiceFragment实例
         */
        fun newOldVoiceAgentInstance(url: String?, token: String?): Fragment {
            return VoiceFragment.newInstance(
                url ?: "wss://your-server.livekit.cloud",
                token ?: "your-jwt-token"
            )
        }
        
        /**
         * 创建旧版MainFragment实例 - 已删除，使用SimpleFragment替代
         */
        fun newOldMainInstance(url: String?, token: String?): Fragment {
            return SimpleFragment.newInstance(
                url ?: "wss://your-server.livekit.cloud",
                token ?: "your-jwt-token"
            )
        }
        
        /**
         * 显示主Fragment（SimpleFragment）
         */
        fun showMainFragment(
            activity: FragmentActivity,
            containerId: Int,
            url: String? = null,
            token: String? = null,
            addToBackStack: Boolean = false
        ) {
            val fragment = newInstance(url, token)
            val transaction = activity.supportFragmentManager.beginTransaction()
                .replace(containerId, fragment)
            
            if (addToBackStack) {
                transaction.addToBackStack(null)
            }
            
            transaction.commitAllowingStateLoss()
        }
        
        /**
         * 显示Kimi Fragment（CompleteFragment）
         */
        fun showKimiFragment(
            activity: FragmentActivity,
            containerId: Int,
            url: String? = null,
            token: String? = null,
            addToBackStack: Boolean = false
        ) {
            val fragment = newCompleteInstance(url, token)
            val transaction = activity.supportFragmentManager.beginTransaction()
                .replace(containerId, fragment)
            
            if (addToBackStack) {
                transaction.addToBackStack(null)
            }
            
            transaction.commitAllowingStateLoss()
        }
        
        /**
         * 显示连接页面（使用SimpleFragment作为连接页面）
         */
        fun showConnectScreen(
            activity: FragmentActivity,
            containerId: Int,
            url: String? = null,
            token: String? = null,
            addToBackStack: Boolean = false
        ) {
            val fragment = newInstance(url, token)
            val transaction = activity.supportFragmentManager.beginTransaction()
                .replace(containerId, fragment)
            
            if (addToBackStack) {
                transaction.addToBackStack(null)
            }
            
            transaction.commitAllowingStateLoss()
        }
        
        /**
         * 显示原始波纹Fragment
         */
        fun showWaveFragment(
            activity: FragmentActivity,
            containerId: Int,
            url: String? = null,
            token: String? = null,
            addToBackStack: Boolean = false
        ) {
            val fragment = newOriginalWaveInstance(url, token)
            val transaction = activity.supportFragmentManager.beginTransaction()
                .replace(containerId, fragment)
            
            if (addToBackStack) {
                transaction.addToBackStack(null)
            }
            
            transaction.commitAllowingStateLoss()
        }
        
        /**
         * 检查当前是否显示SimpleFragment
         */
        fun isShowingMainFragment(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is SimpleFragment
        }
        
        /**
         * 检查当前是否显示CompleteFragment
         */
        fun isShowingKimiFragment(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is CompleteFragment
        }
        
        /**
         * 检查当前是否显示ConnectScreen（使用SimpleFragment）
         */
        fun isShowingConnectScreen(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is SimpleFragment
        }
        
        /**
         * 检查当前是否显示WaveFragment
         */
        fun isShowingWaveFragment(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is WaveFragment
        }
        
        /**
         * 显示旧版语音助手Fragment
         */
        fun showOldVoiceAgentFragment(
            activity: FragmentActivity,
            url: String? = null,
            token: String? = null
        ) {
            val fragment = newOldVoiceAgentInstance(url, token)
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack("old_voice_agent")
                .commit()
        }
        
        /**
         * 检查当前是否显示旧版语音助手Fragment
         */
        fun isShowingOldVoiceAgentFragment(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is VoiceFragment
        }
        
        /**
         * 显示旧版主Fragment
         */
        fun showOldMainFragment(
            activity: FragmentActivity,
            url: String? = null,
            token: String? = null
        ) {
            val fragment = newOldMainInstance(url, token)
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack("old_main")
                .commit()
        }
        
        /**
         * 检查当前是否显示旧版主Fragment
         */
        fun isShowingOldMainFragment(activity: FragmentActivity): Boolean {
            val currentFragment = activity.supportFragmentManager.findFragmentById(android.R.id.content)
            return currentFragment is SimpleFragment
        }
        
        /**
         * 获取当前Fragment类型
         */
        fun getCurrentFragmentType(activity: FragmentActivity): String {
            return when {
                isShowingMainFragment(activity) -> "SimpleFragment"
                isShowingKimiFragment(activity) -> "CompleteFragment"
                isShowingConnectScreen(activity) -> "ConnectScreen"
                isShowingWaveFragment(activity) -> "WaveFragment"
                isShowingOldVoiceAgentFragment(activity) -> "OldVoiceAgentFragment"
                isShowingOldMainFragment(activity) -> "OldMainFragment"
                else -> "Unknown"
            }
        }
        
        /**
         * 清除所有Fragment
         */
        fun clearAllFragments(activity: FragmentActivity) {
            activity.supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        
        /**
         * 返回上一个Fragment
         */
        fun goBack(activity: FragmentActivity): Boolean {
            return if (activity.supportFragmentManager.backStackEntryCount > 0) {
                activity.supportFragmentManager.popBackStack()
                true
            } else {
                false
            }
        }
    }
}