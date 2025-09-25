package com.livekit.voiceassistant.core

import android.net.Uri
import java.util.*

/**
 * 通用聊天消息数据结构
 * 支持多种RTC服务，不依赖具体实现
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val userId: String,
    val userName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType = ChatMessageType.TEXT,
    val mediaUri: String? = null,
    val mediaFileName: String? = null,
    val mediaSize: Long? = null,
    val isLocal: Boolean = false // 是否为本地用户发送的消息
)

/**
 * 消息类型枚举
 */
enum class ChatMessageType {
    TEXT,     // 文本消息
    IMAGE,    // 图片消息
    AUDIO,    // 音频消息
    VIDEO,    // 视频消息
    FILE,     // 文件消息
    SYSTEM    // 系统消息
}

/**
 * 通用连接状态
 */
enum class RTCConnectionState {
    DISCONNECTED,    // 未连接
    CONNECTING,      // 连接中
    CONNECTED,       // 已连接
    RECONNECTING,    // 重连中
    ERROR           // 连接错误
}

/**
 * RTC配置数据类
 */
data class RTCConfig(
    val serverUrl: String,
    val token: String,
    val userId: String,
    val userName: String? = null,
    val enableAudio: Boolean = true,
    val enableVideo: Boolean = false,
    val enableDataChannel: Boolean = true
)

/**
 * 音频状态数据类
 */
data class AudioState(
    val amplitude: Float = 0f,        // 音频振幅 0-1
    val isActive: Boolean = false,    // 是否有音频活动
    val isMuted: Boolean = false      // 是否静音
)

/**
 * 远程参与者信息
 */
data class RemoteParticipant(
    val id: String,
    val name: String? = null,
    val audioState: AudioState = AudioState(),
    val isConnected: Boolean = false
)
