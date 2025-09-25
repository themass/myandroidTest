package com.livekit.voiceassistant

import android.content.Context
import android.widget.Toast
import com.github.ajalt.timberkt.Timber
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*

/**
 * 安全的 LiveKit 连接管理器
 * 提供参数验证和错误处理
 */
object SafeConnectionManager {
    
    /**
     * 检查网络连接
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Timber.e(e) { "检查网络连接失败" }
            false
        }
    }
    
    /**
     * 验证 Token 格式
     */
    fun isValidToken(token: String): Boolean {
        return try {
            token.isNotEmpty() && 
            token.contains(".") && 
            token.split(".").size == 3 &&
            token.length > 50 // 基本长度检查
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 验证 URL 格式
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            url.startsWith("wss://") || url.startsWith("ws://")
        } catch (e: Exception) {
            false
        }
    }
}
