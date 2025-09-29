package com.livekit.voiceassistant.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * RTC代理模式实现
 * 提供统一的RTC服务接口，支持运行时切换不同的RTC实现
 * 
 * 使用方式：
 * 1. 创建RTCProxy实例
 * 2. 通过setAdapter()方法设置具体的RTC实现
 * 3. 所有UI组件通过RTCProxy访问RTC功能
 * 4. 需要切换RTC服务时，只需调用setAdapter()即可
 */
class RTCProxy : RTCAdapter {
    
    private var _adapter: RTCAdapter? = null
    private val adapter: RTCAdapter
        get() = _adapter ?: throw IllegalStateException("RTC适配器未设置，请先调用setAdapter()")
    
    /**
     * 设置RTC适配器
     * 支持运行时切换不同的RTC实现
     */
    fun setAdapter(adapter: RTCAdapter) {
        // 释放旧的适配器
        _adapter?.release()
        _adapter = adapter
    }
    
    /**
     * 获取当前使用的RTC服务类型
     */
    fun getCurrentServiceType(): String {
        return when (_adapter) {
            is LiveKitRTCAdapter -> "LiveKit"
            // is TencentRTCAdapter -> "腾讯云RTC"
            // is AgoraRTCAdapter -> "声网Agora"
            // is NetEaseRTCAdapter -> "网易云信"
            else -> "未知"
        }
    }
    
    // ============ 代理所有RTCAdapter方法 ============
    
    override suspend fun connect(url: String, token: String): Result<Unit> {
        return adapter.connect(url, token)
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return adapter.disconnect()
    }
    
    override val connectionState: StateFlow<RTCConnectionState>
        get() = adapter.connectionState
    
    override val localUserId: StateFlow<String?>
        get() = adapter.localUserId
    
    override val remoteParticipants: StateFlow<List<RemoteParticipant>>
        get() = adapter.remoteParticipants
    
    override suspend fun sendTextMessage(message: String): Result<Unit> {
        return adapter.sendTextMessage(message)
    }
    
    override suspend fun sendMediaMessage(
        type: ChatMessageType,
        mediaUri: String,
        fileName: String?
    ): Result<Unit> {
        return adapter.sendMediaMessage(type, mediaUri, fileName)
    }
    
    override val receivedMessages: StateFlow<List<ChatMessage>>
        get() = adapter.receivedMessages
    
    override fun addLocalMessage(message: ChatMessage) {
        adapter.addLocalMessage(message)
    }
    
    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
        return adapter.setMicrophoneEnabled(enabled)
    }
    
    override val localAudioAmplitude: StateFlow<Float>
        get() = adapter.localAudioAmplitude
    
    override val remoteAudioAmplitude: StateFlow<Float>
        get() = adapter.remoteAudioAmplitude
    
    override val isMicrophoneEnabled: StateFlow<Boolean>
        get() = adapter.isMicrophoneEnabled
    
    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> {
        return adapter.setCameraEnabled(enabled)
    }
    
    override val isCameraEnabled: StateFlow<Boolean>
        get() = adapter.isCameraEnabled
    
    override fun release() {
        adapter.release()
    }
}

/**
 * RTC代理管理器
 * 单例模式，全局管理RTC代理实例
 */
object RTCProxyManager {
    
    private val _proxy = RTCProxy()
    
    /**
     * 获取全局RTC代理实例
     */
    fun getProxy(): RTCProxy = _proxy
    
    /**
     * 切换到LiveKit服务
     */
    fun switchToLiveKit(room: io.livekit.android.room.Room) {
        val adapter = LiveKitRTCAdapter(room)
        _proxy.setAdapter(adapter)
    }
    
    /**
     * 切换到腾讯云RTC服务
     */
    fun switchToTencentRTC(/* 腾讯云参数 */) {
        // val adapter = TencentRTCAdapter(...)
        // _proxy.setAdapter(adapter)
        TODO("腾讯云RTC适配器待实现")
    }
    
    /**
     * 切换到声网Agora服务
     */
    fun switchToAgora(/* Agora参数 */) {
        // val adapter = AgoraRTCAdapter(...)
        // _proxy.setAdapter(adapter)
        TODO("声网Agora适配器待实现")
    }
    
    /**
     * 获取当前服务类型
     */
    fun getCurrentServiceType(): String {
        return _proxy.getCurrentServiceType()
    }
}

/**
 * Compose辅助函数：使用RTC代理
 */
@Composable
fun rememberRTCProxy(): RTCProxy {
    return remember { RTCProxyManager.getProxy() }
}

/**
 * 使用示例：
 * 
 * // 1. 在Application或Activity中初始化
 * RTCProxyManager.switchToLiveKit(room)
 * 
 * // 2. 在Compose中使用
 * @Composable
 * fun MyScreen() {
 *     val rtcProxy = rememberRTCProxy()
 *     
 *     // 监听连接状态
 *     val connectionState by rtcProxy.connectionState.collectAsState()
 *     
 *     // 发送消息
 *     LaunchedEffect(Unit) {
 *         rtcProxy.sendTextMessage("Hello")
 *     }
 * }
 * 
 * // 3. 运行时切换RTC服务
 * fun switchRTCService() {
 *     when (currentService) {
 *         "LiveKit" -> RTCProxyManager.switchToTencentRTC()
 *         "Tencent" -> RTCProxyManager.switchToAgora()
 *         "Agora" -> RTCProxyManager.switchToLiveKit(room)
 *     }
 * }
 */
