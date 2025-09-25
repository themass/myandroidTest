package com.livekit.voiceassistant.core

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow

/**
 * RTC服务管理器
 * 统一管理不同RTC服务的创建和切换
 */
object RTCServiceManager {
    
    /**
     * RTC服务类型
     */
    enum class RTCServiceType {
        LIVEKIT,      // LiveKit
        TENCENT,      // 腾讯云RTC
        AGORA,        // 声网Agora
        NETEASE,      // 网易云信
        WEBRTC        // 原生WebRTC
    }
    
    /**
     * 创建RTC适配器
     */
    fun createAdapter(
        type: RTCServiceType,
        room: Any? = null
    ): RTCAdapter {
        return when (type) {
            RTCServiceType.LIVEKIT -> {
                require(room is io.livekit.android.room.Room) { "LiveKit需要Room对象" }
                LiveKitRTCAdapter(room)
            }
            RTCServiceType.TENCENT -> {
                TODO("腾讯云RTC适配器待实现")
            }
            RTCServiceType.AGORA -> {
                TODO("声网Agora适配器待实现")
            }
            RTCServiceType.NETEASE -> {
                TODO("网易云信适配器待实现")
            }
            RTCServiceType.WEBRTC -> {
                TODO("原生WebRTC适配器待实现")
            }
        }
    }
}

/**
 * Compose辅助函数：记住RTC适配器
 */
@Composable
fun rememberRTCAdapter(
    type: RTCServiceManager.RTCServiceType,
    room: Any? = null
): RTCAdapter {
    return remember(type, room) {
        RTCServiceManager.createAdapter(type, room)
    }
}

/**
 * RTC状态Composable包装器
 * 提供响应式的RTC状态管理
 */
@Composable
fun RTCStateProvider(
    adapter: RTCAdapter,
    content: @Composable (RTCState) -> Unit
) {
    val connectionState by adapter.connectionState.collectAsState()
    val localUserId by adapter.localUserId.collectAsState()
    val remoteParticipants by adapter.remoteParticipants.collectAsState()
    val messages by adapter.receivedMessages.collectAsState()
    val localAudioAmplitude by adapter.localAudioAmplitude.collectAsState()
    val remoteAudioAmplitude by adapter.remoteAudioAmplitude.collectAsState()
    val isMicrophoneEnabled by adapter.isMicrophoneEnabled.collectAsState()
    
    val rtcState = RTCState(
        connectionState = connectionState,
        localUserId = localUserId,
        remoteParticipants = remoteParticipants,
        messages = messages,
        localAudioAmplitude = localAudioAmplitude,
        remoteAudioAmplitude = remoteAudioAmplitude,
        isMicrophoneEnabled = isMicrophoneEnabled,
        adapter = adapter
    )
    
    content(rtcState)
}

/**
 * RTC状态数据类
 * 包含所有RTC相关的响应式状态
 */
data class RTCState(
    val connectionState: RTCConnectionState,
    val localUserId: String?,
    val remoteParticipants: List<RemoteParticipant>,
    val messages: List<ChatMessage>,
    val localAudioAmplitude: Float,
    val remoteAudioAmplitude: Float,
    val isMicrophoneEnabled: Boolean,
    val adapter: RTCAdapter
)
