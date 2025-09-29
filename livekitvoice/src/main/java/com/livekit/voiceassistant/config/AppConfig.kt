package com.livekit.voiceassistant.config

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 应用配置管理
 * 集中管理所有配置信息，避免硬编码
 */
object AppConfig {
    
    // ============ RTC配置 ============
    
    /**
     * 默认RTC服务URL
     */
    const val DEFAULT_RTC_URL = "wss://your-server.livekit.cloud"
    
    /**
     * 默认Token
     */
    const val DEFAULT_TOKEN = "your-jwt-token"
    
    /**
     * 连接超时时间（毫秒）
     */
    const val CONNECTION_TIMEOUT = 10000L
    
    /**
     * 重连间隔（毫秒）
     */
    const val RECONNECT_INTERVAL = 3000L
    
    /**
     * 最大重连次数
     */
    const val MAX_RECONNECT_ATTEMPTS = 3
    
    // ============ 音频配置 ============
    
    /**
     * 音频检测更新频率（毫秒）
     */
    const val AUDIO_DETECTION_INTERVAL = 50L
    
    /**
     * Agent振幅放大倍数
     */
    const val AGENT_AMPLITUDE_MULTIPLIER = 3000
    
    /**
     * 用户振幅放大倍数
     */
    const val USER_AMPLITUDE_MULTIPLIER = 3500
    
    /**
     * 默认波浪动画频率
     */
    const val DEFAULT_WAVE_FREQUENCY = 0.002f
    
    /**
     * 默认波浪振幅范围
     */
    const val DEFAULT_WAVE_AMPLITUDE_MIN = 0.2f
    const val DEFAULT_WAVE_AMPLITUDE_MAX = 0.8f
    
    // ============ UI配置 ============
    
    /**
     * 波纹动画持续时间（毫秒）
     */
    const val WAVE_ANIMATION_DURATION = 300
    
    /**
     * 波纹缩放比例
     */
    const val WAVE_SCALE_ACTIVE = 1.2f
    const val WAVE_SCALE_INACTIVE = 1.0f
    
    /**
     * 波纹大小
     */
    val WAVE_SIZE_CIRCULAR = 200.dp
    val WAVE_SIZE_LINEAR = 300.dp to 100.dp
    val WAVE_SIZE_PARTICLE = 250.dp
    
    // ============ 颜色配置 ============
    
    /**
     * 波纹颜色
     */
    val WAVE_COLOR_AGENT = Color(0xFF4ECDC4)
    val WAVE_RIPPLE_COLOR_AGENT = Color(0xFF45B7B8)
    val WAVE_COLOR_USER = Color(0xFFFF6B6B)
    val WAVE_RIPPLE_COLOR_USER = Color(0xFFFF8E8E)
    val WAVE_COLOR_PARTICLE = Color(0xFF9B59B6)
    val WAVE_RIPPLE_COLOR_PARTICLE = Color(0xFF8E44AD)
    
    /**
     * 连接状态颜色
     */
    val CONNECTION_COLOR_CONNECTING = Color(0xFFFFA500)
    val CONNECTION_COLOR_CONNECTED = Color(0xFFFF6B6B)
    val CONNECTION_COLOR_DISCONNECTED = Color(0xFF4ECDC4)
    
    // ============ 消息配置 ============
    
    /**
     * 消息显示持续时间（毫秒）
     */
    const val MESSAGE_DISPLAY_DURATION = 2000L
    
    /**
     * 最大消息数量
     */
    const val MAX_MESSAGE_COUNT = 100
    
    // ============ 错误配置 ============
    
    /**
     * 错误显示持续时间（毫秒）
     */
    const val ERROR_DISPLAY_DURATION = 3000L
    
    /**
     * 日志标签
     */
    const val LOG_TAG_AUDIO = "AudioDetector"
    const val LOG_TAG_RTC = "RTCAdapter"
    const val LOG_TAG_UI = "UIManager"
    const val LOG_TAG_ERROR = "ErrorHandler"
    
    // ============ 功能开关 ============
    
    /**
     * 是否启用音频检测
     */
    const val ENABLE_AUDIO_DETECTION = true
    
    /**
     * 是否启用波纹动画
     */
    const val ENABLE_WAVE_ANIMATION = true
    
    /**
     * 是否启用消息持久化
     */
    const val ENABLE_MESSAGE_PERSISTENCE = false
    
    /**
     * 是否启用调试日志
     */
    const val ENABLE_DEBUG_LOGGING = true
    
    // ============ 波纹背景配置 ============
    
    /**
     * 波纹背景透明度
     */
    const val WAVE_BACKGROUND_ALPHA = 0.05f
}
