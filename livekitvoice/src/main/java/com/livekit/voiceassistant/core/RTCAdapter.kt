package com.livekit.voiceassistant.core

import kotlinx.coroutines.flow.StateFlow

/**
 * RTC服务适配器接口
 * 抽象了所有RTC服务的通用功能，支持LiveKit、腾讯云RTC、声网等
 */
interface RTCAdapter {
    
    // ============ 连接管理 ============
    
    /**
     * 连接到RTC服务
     */
    suspend fun connect(url: String, token: String): Result<Unit>
    
    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * 连接状态流
     */
    val connectionState: StateFlow<RTCConnectionState>
    
    /**
     * 本地用户ID
     */
    val localUserId: StateFlow<String?>
    
    /**
     * 远程参与者列表
     */
    val remoteParticipants: StateFlow<List<RemoteParticipant>>
    
    // ============ 消息通道 ============
    
    /**
     * 发送文本消息
     */
    suspend fun sendTextMessage(message: String): Result<Unit>
    
    /**
     * 发送多媒体消息
     */
    suspend fun sendMediaMessage(
        type: ChatMessageType,
        mediaUri: String,
        fileName: String? = null
    ): Result<Unit>
    
    /**
     * 接收到的消息流
     */
    val receivedMessages: StateFlow<List<ChatMessage>>
    
    /**
     * 手动添加本地消息（用于显示）
     */
    fun addLocalMessage(message: ChatMessage)
    
    // ============ 音频管理 ============
    
    /**
     * 设置麦克风状态
     */
    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>
    
    /**
     * 本地音频振幅
     */
    val localAudioAmplitude: StateFlow<Float>
    
    /**
     * 远程音频振幅（通常是Agent）
     */
    val remoteAudioAmplitude: StateFlow<Float>
    
    /**
     * 麦克风状态
     */
    val isMicrophoneEnabled: StateFlow<Boolean>
    
    // ============ 可选功能 ============
    
    /**
     * 设置摄像头状态（可选）
     */
    suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)
    
    /**
     * 摄像头状态（可选）
     */
    val isCameraEnabled: StateFlow<Boolean>
        get() = kotlinx.coroutines.flow.MutableStateFlow(false)
    
    /**
     * 释放资源
     */
    fun release()
}

/**
 * RTC适配器工厂
 */
object RTCAdapterFactory {
    
    /**
     * 创建LiveKit适配器
     */
    fun createLiveKitAdapter(room: io.livekit.android.room.Room): RTCAdapter {
        return LiveKitRTCAdapter(room)
    }
    
    /**
     * 创建腾讯云RTC适配器
     */
    fun createTencentRTCAdapter(/* 腾讯云参数 */): RTCAdapter {
        // return TencentRTCAdapter(...)
        TODO("腾讯云RTC适配器待实现")
    }
    
    /**
     * 创建声网Agora适配器
     */
    fun createAgoraAdapter(/* Agora参数 */): RTCAdapter {
        // return AgoraRTCAdapter(...)
        TODO("声网Agora适配器待实现")
    }
}
