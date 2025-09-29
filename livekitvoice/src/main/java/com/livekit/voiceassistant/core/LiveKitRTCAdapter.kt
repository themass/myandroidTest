package com.livekit.voiceassistant.core

import io.livekit.android.room.Room
import io.livekit.android.room.ConnectionState as LiveKitConnectionState
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.datastream.TextStreamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * LiveKit RTC适配器实现
 * 将LiveKit的API适配到通用RTC接口
 */
class LiveKitRTCAdapter(
    private val room: Room,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : RTCAdapter {
    
    // ============ 状态管理 ============
    
    private val _connectionState = MutableStateFlow(RTCConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RTCConnectionState> = _connectionState.asStateFlow()
    
    private val _localUserId = MutableStateFlow<String?>(null)
    override val localUserId: StateFlow<String?> = _localUserId.asStateFlow()
    
    private val _remoteParticipants = MutableStateFlow<List<RemoteParticipant>>(emptyList())
    override val remoteParticipants: StateFlow<List<RemoteParticipant>> = _remoteParticipants.asStateFlow()
    
    private val _receivedMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val receivedMessages: StateFlow<List<ChatMessage>> = _receivedMessages.asStateFlow()
    
    private val _localAudioAmplitude = MutableStateFlow(0f)
    override val localAudioAmplitude: StateFlow<Float> = _localAudioAmplitude.asStateFlow()
    
    private val _remoteAudioAmplitude = MutableStateFlow(0f)
    override val remoteAudioAmplitude: StateFlow<Float> = _remoteAudioAmplitude.asStateFlow()
    
    private val _isMicrophoneEnabled = MutableStateFlow(false)
    override val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()
    
    init {
        // 初始化状态监听
        initializeStateListeners()
    }
    
    // ============ 连接管理 ============
    
    override suspend fun connect(url: String, token: String): Result<Unit> {
        return try {
            _connectionState.value = RTCConnectionState.CONNECTING
            
            // LiveKit连接逻辑
            room.connect(url, token)
            
            // 更新状态
            _connectionState.value = RTCConnectionState.CONNECTED
            _localUserId.value = room.localParticipant.identity?.toString()
            
            // 启用音频
            setMicrophoneEnabled(true)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = RTCConnectionState.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return try {
            room.disconnect()
            _connectionState.value = RTCConnectionState.DISCONNECTED
            _localUserId.value = null
            _remoteParticipants.value = emptyList()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============ 消息通道 ============
    
    override suspend fun sendTextMessage(message: String): Result<Unit> {
        return try {
            val sender = room.localParticipant.streamText(
                StreamTextOptions(
                    operationType = TextStreamInfo.OperationType.CREATE,
                    topic = "lk.chat",
                    attributes = mapOf(
                        "message_type" to "text",
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
            )
            sender.write(message)
            sender.close()
            
            // 添加到本地消息列表
            val chatMessage = ChatMessage(
                text = message,
                userId = _localUserId.value ?: "unknown",
                isLocal = true
            )
            addLocalMessage(chatMessage)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendMediaMessage(
        type: ChatMessageType,
        mediaUri: String,
        fileName: String?
    ): Result<Unit> {
        return try {
            // 发送媒体消息URL到数据通道
            val sender = room.localParticipant.streamText(
                StreamTextOptions(
                    operationType = TextStreamInfo.OperationType.CREATE,
                    topic = "lk.media",
                    attributes = mapOf(
                        "message_type" to type.name.lowercase(),
                        "media_uri" to mediaUri,
                        "file_name" to (fileName ?: ""),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
            )
            sender.write(mediaUri)
            sender.close()
            
            // 添加到本地消息列表
            val chatMessage = ChatMessage(
                text = fileName ?: "[${type.name}消息]",
                userId = _localUserId.value ?: "unknown",
                type = type,
                mediaUri = mediaUri,
                mediaFileName = fileName,
                isLocal = true
            )
            addLocalMessage(chatMessage)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun addLocalMessage(message: ChatMessage) {
        val currentMessages = _receivedMessages.value.toMutableList()
        currentMessages.add(message)
        _receivedMessages.value = currentMessages
    }
    
    // ============ 音频管理 ============
    
    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
        return try {
            room.localParticipant.setMicrophoneEnabled(enabled)
            _isMicrophoneEnabled.value = enabled
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============ 状态监听初始化 ============
    
    private fun initializeStateListeners() {
        scope.launch {
            // 监听LiveKit连接状态
            val identity = room.localParticipant.identity
            if (identity != null) {
                _connectionState.value = RTCConnectionState.CONNECTED
                _localUserId.value = identity.toString()
            } else {
                _connectionState.value = RTCConnectionState.DISCONNECTED
            }
            
            // 监听真实的LiveKit音频数据
            while (true) {
                if (_connectionState.value == RTCConnectionState.CONNECTED) {
                    // 监听本地音频振幅 - 基于麦克风状态
                    _localAudioAmplitude.value = if (_isMicrophoneEnabled.value) {
                        // 这里可以集成真实的音频振幅检测
                        // 目前使用基于连接状态的简单检测
                        if (room.localParticipant.isSpeaking) {
                            0.6f // 本地用户正在说话
                        } else {
                            0.1f // 静音但有连接
                        }
                    } else {
                        0f
                    }
                    
                    // 监听远程音频振幅 - 基于远程参与者状态
                    val remoteParticipants = room.remoteParticipants
                    if (remoteParticipants.isNotEmpty()) {
                        // 检查是否有远程参与者在说话
                        val activeSpeaker = remoteParticipants.values.find { participant ->
                            participant.isSpeaking
                        }
                        _remoteAudioAmplitude.value = if (activeSpeaker != null) {
                            0.8f // 远程参与者正在说话
                        } else {
                            0.1f // 静音但有连接
                        }
                        
                        // 更新远程参与者列表
                        val remoteList = remoteParticipants.values.map { participant ->
                            RemoteParticipant(
                                id = participant.identity.toString(),
                                name = participant.name,
                                audioState = AudioState(
                                    amplitude = if (participant.isSpeaking) 0.8f else 0.1f,
                                    isActive = participant.isSpeaking
                                ),
                                isConnected = true
                            )
                        }
                        _remoteParticipants.value = remoteList
                    } else {
                        _remoteAudioAmplitude.value = 0f
                        _remoteParticipants.value = emptyList()
                    }
                    
                    delay(100) // 100ms更新一次
                } else {
                    _localAudioAmplitude.value = 0f
                    _remoteAudioAmplitude.value = 0f
                    _remoteParticipants.value = emptyList()
                    delay(500)
                }
            }
        }
    }
    
    // ============ 资源释放 ============
    
    override fun release() {
        scope.launch {
            try {
                room.disconnect()
                room.release()
            } catch (e: Exception) {
                // 忽略释放时的错误
            }
        }
    }
}
