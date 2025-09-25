package com.livekit.voiceassistant

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import io.livekit.android.room.RoomException
import com.github.ajalt.timberkt.Timber

/**
 * LiveKit 错误处理工具类
 */
object ErrorHandler {
    
    /**
     * 处理 LiveKit 连接错误
     */
    fun handleConnectionError(activity: ComponentActivity, exception: Throwable) {
        when (exception) {
            is RoomException.ConnectException -> {
                when {
                    exception.message?.contains("401") == true -> {
                        showError(activity, "认证失败：Token 可能已过期，请检查配置")
                        Timber.e { "LiveKit 认证失败: ${exception.message}" }
                    }
                    exception.message?.contains("403") == true -> {
                        showError(activity, "权限不足：请检查 Token 权限配置")
                        Timber.e { "LiveKit 权限不足: ${exception.message}" }
                    }
                    exception.message?.contains("404") == true -> {
                        showError(activity, "房间不存在：请检查房间名称")
                        Timber.e { "LiveKit 房间不存在: ${exception.message}" }
                    }
                    exception.message?.contains("Could not fetch region settings") == true -> {
                        showError(activity, "网络连接失败：无法获取区域设置")
                        Timber.e { "LiveKit 网络连接失败: ${exception.message}" }
                    }
                    else -> {
                        showError(activity, "连接失败：${exception.message}")
                        Timber.e { "LiveKit 连接失败: ${exception.message}" }
                    }
                }
            }
            // 其他 RoomException 类型
            is RoomException -> {
                showError(activity, "LiveKit 错误：${exception.message}")
                Timber.e { "LiveKit 错误: ${exception.message}" }
            }
            else -> {
                showError(activity, "未知错误：${exception.message}")
                Timber.e { "LiveKit 未知错误: ${exception.message}" }
            }
        }
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(activity: ComponentActivity, message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 检查 Token 是否有效
     */
    fun isTokenValid(token: String): Boolean {
        return try {
            // 简单的 Token 格式检查
            token.isNotEmpty() && token.contains(".") && token.split(".").size == 3
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取用户友好的错误信息
     */
    fun getUserFriendlyMessage(exception: Throwable): String {
        return when (exception) {
            is RoomException.ConnectException -> {
                when {
                    exception.message?.contains("401") == true -> "认证失败，请检查 Token"
                    exception.message?.contains("403") == true -> "权限不足，请检查配置"
                    exception.message?.contains("404") == true -> "房间不存在"
                    exception.message?.contains("Could not fetch region settings") == true -> "网络连接失败"
                    else -> "连接失败"
                }
            }
            is RoomException -> "LiveKit 错误"
            else -> "发生未知错误"
        }
    }
}
