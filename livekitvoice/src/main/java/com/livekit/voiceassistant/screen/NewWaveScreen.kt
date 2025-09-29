package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.ConnectionStatusView
import com.livekit.voiceassistant.component.AudioVisualizer
import com.livekit.voiceassistant.component.KimiWaveType
import com.livekit.voiceassistant.core.ChatMessage
import com.livekit.voiceassistant.core.RTCConnectionState
import com.livekit.voiceassistant.ui.ChatLog
import com.livekit.voiceassistant.audio.rememberAudioDetector
import com.livekit.voiceassistant.audio.rememberConnectionDetector
import com.livekit.voiceassistant.audio.rememberMicrophoneManager
import com.livekit.voiceassistant.config.AppConfig
import com.livekit.voiceassistant.error.ErrorDisplay
import com.livekit.voiceassistant.error.GlobalErrorHandler
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.room.Room
import com.livekit.voiceassistant.datastreams.TranscriptionsState
import kotlinx.coroutines.launch

/**
 * 新版波纹效果页面 - 使用AudioVisualizer组件
 * 支持多种波形类型：条形、粒子、自定义波纹
 * 与自定义波纹效果页面区分开
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWaveScreen(
    room: Room,
    transcriptionsState: TranscriptionsState,
    url: String,
    token: String,
    onBack: () -> Unit
) {
    // ============ 状态管理 ============
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var agentAmplitude by remember { mutableStateOf(0f) }
    var userAmplitude by remember { mutableStateOf(0f) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isMicrophoneEnabled by remember { mutableStateOf(false) }
    var currentWaveType by remember { mutableStateOf(KimiWaveType.PARTICLE) } // 默认粒子效果
    
    val coroutineScope = rememberCoroutineScope()
    
    // ============ 使用提取的公共逻辑 ============
    
    // 连接状态检测
    rememberConnectionDetector(room) { connected ->
        isConnected = connected
    }
    
    // 麦克风管理
    rememberMicrophoneManager(room) { enabled ->
        isMicrophoneEnabled = enabled
    }
    
    // 音频检测
    rememberAudioDetector(
        room = room,
        transcriptionsState = transcriptionsState,
        onAgentAmplitudeChange = { amplitude ->
            agentAmplitude = amplitude
        },
        onUserAmplitudeChange = { amplitude ->
            userAmplitude = amplitude
        },
        onMessageAdd = { message ->
            // 避免重复添加消息
            if (messages.isEmpty() || messages.last().text != message.text) {
                messages = messages + message
            }
        }
    )
    
    // ============ 动画效果 ============
    val scale by animateFloatAsState(
        targetValue = if (agentAmplitude > 0.1f) AppConfig.WAVE_SCALE_ACTIVE else AppConfig.WAVE_SCALE_INACTIVE,
        animationSpec = tween(AppConfig.WAVE_ANIMATION_DURATION),
        label = "waveScale"
    )
    
    // ============ UI布局 ============
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 错误显示
        ErrorDisplay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )
        
        // 顶部状态栏
        ConnectionStatusView(
            connectionState = when {
                isConnecting -> RTCConnectionState.CONNECTING
                isConnected -> RTCConnectionState.CONNECTED
                else -> RTCConnectionState.DISCONNECTED
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        // 中间消息区域
        ChatLog(
            room = room,
            transcriptions = transcriptionsState.transcriptions.value,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 200.dp)
        )
        
        // 新版波纹效果区域 - 使用AudioVisualizer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            // Agent波纹效果 - 中心圆形
            AudioVisualizer(
                isActive = agentAmplitude > 0.1f,
                amplitude = agentAmplitude,
                waveType = currentWaveType,
                modifier = Modifier
                    .size(200.dp)
            )
            
            // 用户波纹效果 - 底部线性
            AudioVisualizer(
                isActive = userAmplitude > 0.1f,
                amplitude = userAmplitude,
                waveType = KimiWaveType.BAR, // 用户使用条形波纹
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .size(300.dp, 60.dp)
            )
        }
        
        // 波形类型切换按钮
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 粒子效果按钮
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.PARTICLE },
                label = { Text("粒子") },
                selected = currentWaveType == KimiWaveType.PARTICLE,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
            
            // 条形效果按钮
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.BAR },
                label = { Text("条形") },
                selected = currentWaveType == KimiWaveType.BAR,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
            
            // 自定义波纹按钮
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.CUSTOM },
                label = { Text("波纹") },
                selected = currentWaveType == KimiWaveType.CUSTOM,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
        }
        
        // 底部控制区域
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 连接/断开按钮
            Button(
                onClick = {
                    if (isConnected) {
                        // 断开连接
                        try {
                            agentAmplitude = 0f
                            userAmplitude = 0f
                            messages = emptyList()
                            room.disconnect()
                            isConnected = false
                            println("✅ 新版波纹: 已断开连接")
                        } catch (e: Exception) {
                            GlobalErrorHandler.addError(
                                GlobalErrorHandler.createConnectionError("断开连接失败: ${e.message}")
                            )
                        }
                    } else {
                        // 连接
                        isConnecting = true
                        coroutineScope.launch {
                            try {
                                room.connect(url, token)
                                isConnected = true
                                isConnecting = false
                                println("✅ 新版波纹: 连接成功")
                            } catch (e: Exception) {
                                GlobalErrorHandler.addError(
                                    GlobalErrorHandler.createConnectionError("连接失败: ${e.message}")
                                )
                                isConnecting = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isConnecting -> AppConfig.CONNECTION_COLOR_CONNECTING
                        isConnected -> AppConfig.CONNECTION_COLOR_CONNECTED
                        else -> AppConfig.CONNECTION_COLOR_DISCONNECTED
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isConnecting -> "连接中..."
                        isConnected -> "断开连接"
                        else -> "连接服务器"
                    }
                )
            }
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "新版波纹效果页面")
@Composable
fun NewWaveScreenPreview() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(id = R.color.kimi_primary),
            secondary = colorResource(id = R.color.kimi_secondary),
            background = Color.White
        )
    ) {
        NewWaveScreenPreviewContent()
    }
}

@Composable
private fun NewWaveScreenPreviewContent() {
    var isConnected by remember { mutableStateOf(true) }
    var agentAmplitude by remember { mutableStateOf(0.8f) }
    var userAmplitude by remember { mutableStateOf(0.6f) }
    var currentWaveType by remember { mutableStateOf(KimiWaveType.PARTICLE) }
    
    val scale by animateFloatAsState(
        targetValue = if (agentAmplitude > 0.1f) AppConfig.WAVE_SCALE_ACTIVE else AppConfig.WAVE_SCALE_INACTIVE,
        animationSpec = tween(AppConfig.WAVE_ANIMATION_DURATION),
        label = "waveScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 顶部状态栏
        ConnectionStatusView(
            connectionState = if (isConnected) RTCConnectionState.CONNECTED else RTCConnectionState.DISCONNECTED,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        // 新版波纹效果区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            // Agent波纹效果
            AudioVisualizer(
                isActive = agentAmplitude > 0.1f,
                amplitude = agentAmplitude,
                waveType = currentWaveType,
                modifier = Modifier
                    .size(200.dp)
            )
            
            // 用户波纹效果
            AudioVisualizer(
                isActive = userAmplitude > 0.1f,
                amplitude = userAmplitude,
                waveType = KimiWaveType.BAR,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .size(300.dp, 60.dp)
            )
        }
        
        // 波形类型切换按钮
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.PARTICLE },
                label = { Text("粒子") },
                selected = currentWaveType == KimiWaveType.PARTICLE,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
            
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.BAR },
                label = { Text("条形") },
                selected = currentWaveType == KimiWaveType.BAR,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
            
            FilterChip(
                onClick = { currentWaveType = KimiWaveType.CUSTOM },
                label = { Text("波纹") },
                selected = currentWaveType == KimiWaveType.CUSTOM,
                modifier = Modifier.size(height = 32.dp, width = 60.dp)
            )
        }
        
        // 底部控制区域
        Button(
            onClick = { isConnected = !isConnected },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) AppConfig.CONNECTION_COLOR_CONNECTED else AppConfig.CONNECTION_COLOR_DISCONNECTED
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(if (isConnected) "断开连接" else "连接服务器")
        }
    }
}
