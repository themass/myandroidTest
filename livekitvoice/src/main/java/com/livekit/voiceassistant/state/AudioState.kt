package com.livekit.voiceassistant.state

import androidx.compose.runtime.Immutable
import com.livekit.voiceassistant.core.ChatMessage
import com.livekit.voiceassistant.core.RTCConnectionState

/**
 * 音频状态管理
 * 统一管理音频相关的状态
 */
@Immutable
data class AudioState(
    val connectionState: RTCConnectionState = RTCConnectionState.DISCONNECTED,
    val isConnecting: Boolean = false,
    val agentAmplitude: Float = 0f,
    val userAmplitude: Float = 0f,
    val defaultWaveAnimation: Float = 0f,
    val isMicrophoneEnabled: Boolean = false
)

/**
 * 聊天状态管理
 * 统一管理聊天相关的状态
 */
@Immutable
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false
)

/**
 * 应用状态管理
 * 统一管理应用全局状态
 */
@Immutable
data class AppState(
    val audioState: AudioState = AudioState(),
    val chatState: ChatState = ChatState(),
    val error: String? = null
)
