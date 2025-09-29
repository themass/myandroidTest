package com.livekit.voiceassistant.audio

import androidx.compose.runtime.*
import com.livekit.voiceassistant.core.ChatMessage
import com.livekit.voiceassistant.core.ChatMessageType
import com.livekit.voiceassistant.datastreams.TranscriptionsState
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

/**
 * 音频检测器 - 统一音频检测逻辑
 * 提取公共的音频检测代码，避免重复
 */
@Composable
fun rememberAudioDetector(
    room: Room,
    transcriptionsState: TranscriptionsState,
    onAgentAmplitudeChange: (Float) -> Unit,
    onUserAmplitudeChange: (Float) -> Unit,
    onMessageAdd: (ChatMessage) -> Unit
) {
    var defaultWaveAnimation by remember { mutableStateOf(0f) }
    
    // 默认波浪起伏动画
    LaunchedEffect(Unit) {
        while (true) {
            defaultWaveAnimation = (kotlin.math.sin(System.currentTimeMillis() * 0.002f) * 0.4f + 0.4f).coerceIn(0.2f, 0.8f)
            kotlinx.coroutines.delay(50)
        }
    }
    
    // 音频检测逻辑
    LaunchedEffect(room) {
        while (true) {
            try {
                // 检测Agent音频活动 - 基于远程参与者isSpeaking状态
                val remoteParticipants = room.remoteParticipants
                val hasActiveRemoteSpeaker = remoteParticipants.values.any { participant ->
                    participant.isSpeaking
                }
                
                if (hasActiveRemoteSpeaker) {
                    onAgentAmplitudeChange(1.0f)
                    println("🎵 音频检测: 检测到Agent语音活动")
                } else {
                    onAgentAmplitudeChange(0f)
                }
                
                // 检测用户音频活动 - 基于本地参与者状态
                val localParticipant = room.localParticipant
                if (localParticipant.isSpeaking) {
                    onUserAmplitudeChange(1.0f)
                    println("🎵 音频检测: 检测到用户语音活动")
                } else {
                    // 没有语音时使用默认波浪起伏效果
                    onUserAmplitudeChange(defaultWaveAnimation)
                }
                
                // 处理转录消息
                val transcriptions = transcriptionsState.transcriptions.value
                if (transcriptions.isNotEmpty()) {
                    val lastTranscription = transcriptions.last()
                    if (lastTranscription.identity != room.localParticipant.identity) {
                        val agentMessage = ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            text = lastTranscription.transcriptionSegment.text,
                            userId = lastTranscription.identity.toString(),
                            userName = "Agent",
                            timestamp = System.currentTimeMillis(),
                            type = ChatMessageType.TEXT,
                            isLocal = false
                        )
                        onMessageAdd(agentMessage)
                    }
                }
                
            } catch (e: Exception) {
                println("❌ 音频检测错误: ${e.message}")
            }
            
            kotlinx.coroutines.delay(50) // 50ms更新一次
        }
    }
}

/**
 * 连接状态检测器
 */
@Composable
fun rememberConnectionDetector(
    room: Room,
    onConnectionStateChange: (Boolean) -> Unit
) {
    LaunchedEffect(room) {
        while (true) {
            val isConnected = room.localParticipant.identity != null
            onConnectionStateChange(isConnected)
            kotlinx.coroutines.delay(100)
        }
    }
}

/**
 * 麦克风状态管理器
 */
@Composable
fun rememberMicrophoneManager(
    room: Room,
    onMicrophoneStateChange: (Boolean) -> Unit
) {
    LaunchedEffect(room) {
        try {
            room.localParticipant.setMicrophoneEnabled(true)
            onMicrophoneStateChange(true)
            println("✅ 麦克风已启用")
        } catch (e: Exception) {
            onMicrophoneStateChange(false)
            println("❌ 麦克风启用失败: ${e.message}")
        }
    }
}
