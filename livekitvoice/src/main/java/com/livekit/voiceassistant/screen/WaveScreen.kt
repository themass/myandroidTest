package com.livekit.voiceassistant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.ConnectionStatusView
import com.livekit.voiceassistant.component.KimiRecordInputView
import com.livekit.voiceassistant.core.RTCConnectionState
import com.livekit.voiceassistant.datastreams.rememberTranscriptions
import com.livekit.voiceassistant.ui.ChatLog
import com.livekit.voiceassistant.wave.MyWaveView
import io.livekit.android.compose.local.RoomScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 原始自定义波纹版本 - 恢复删除前的版本
 * 
 * 布局结构：
 * - 头部：通用连接状态组件
 * - 中间：通用消息组件 (ChatLog)
 * - 底部：通用连接按钮
 * - 背景：MyWaveView.java 作为虚化背景，振幅与agent和用户语音同步且较大
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveScreen(
    url: String,
    token: String,
    onBack: () -> Unit
) {
    // 使用简洁的白色背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 使用RoomScope来管理LiveKit连接
        RoomScope(
            url = url,
            token = token,
            audio = true,
            video = false,
            connect = false // 不自动连接，由用户手动连接
        ) { room ->
            val transcriptionsState = rememberTranscriptions(room)
            
            // 原始自定义波纹效果展示
            OriginalCustomWaveContent(room, transcriptionsState, url, token, onBack)
        }
    }
}

@Composable
private fun OriginalCustomWaveContent(
    room: io.livekit.android.room.Room,
    transcriptionsState: com.livekit.voiceassistant.datastreams.TranscriptionsState,
    url: String,
    token: String,
    onBack: () -> Unit
) {
    // 状态管理
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var agentAmplitude by remember { mutableStateOf(0f) }
    var userAmplitude by remember { mutableStateOf(0f) }
    var messages by remember { mutableStateOf<List<com.livekit.voiceassistant.core.ChatMessage>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    // 默认波浪起伏效果
    var defaultWaveAnimation by remember { mutableStateOf(0f) }
    
    // 启用麦克风
    LaunchedEffect(room) {
        try {
            room.localParticipant.setMicrophoneEnabled(true)
            println("✅ 原始自定义波纹: 麦克风已启用")
        } catch (e: Exception) {
            println("❌ 原始自定义波纹: 麦克风启用失败: ${e.message}")
        }
    }
    
    // 调试连接参数
    LaunchedEffect(url, token) {
        println("🔍 原始自定义波纹: 连接参数检查")
        println("🔍 URL: $url")
        println("🔍 Token: ${token?.take(50)}...")
        println("🔍 Token长度: ${token?.length}")
    }
    
    // 默认波浪起伏动画 - 像浪一样有规律的起伏
    LaunchedEffect(Unit) {
        while (true) {
            // 使用正弦波创建有规律的起伏效果，增大振幅让效果更明显
            defaultWaveAnimation = (kotlin.math.sin(System.currentTimeMillis() * 0.002f) * 0.4f + 0.4f).coerceIn(0.2f, 0.8f)
            kotlinx.coroutines.delay(50) // 50ms更新一次，流畅的动画
        }
    }
    
    // 监听真实的LiveKit连接状态
    LaunchedEffect(room) {
        if (room.localParticipant.identity != null) {
            isConnected = true
        } else {
            isConnected = false
        }
    }
    
    // 监听真实的LiveKit音频状态 - 更实时和敏感的检测
    LaunchedEffect(room) {
        while (true) {
            if (isConnected) {
                // 检测Agent音频活动 - 基于远程参与者isSpeaking状态
                val remoteParticipants = room.remoteParticipants
                val hasActiveRemoteSpeaker = remoteParticipants.values.any { participant ->
                    participant.isSpeaking
                }
                
                if (hasActiveRemoteSpeaker) {
                    agentAmplitude = 1.0f // 最大振幅，配合3000倍放大
                    println("🎵 原始自定义波纹: 检测到Agent语音活动，振幅: $agentAmplitude (AX: ${(agentAmplitude * 3000).toInt()})")
                } else {
                    agentAmplitude = 0f
                }
                
                // 检测用户音频活动 - 基于本地参与者状态
                val localParticipant = room.localParticipant
                if (localParticipant.isSpeaking) {
                    userAmplitude = 1.0f // 最大振幅，配合3500倍放大
                    println("🎵 原始自定义波纹: 检测到用户语音活动，振幅: $userAmplitude (AX: ${(userAmplitude * 3500).toInt()})")
                } else {
                    // 没有语音时使用默认波浪起伏效果
                    userAmplitude = defaultWaveAnimation
                }
                
                // 处理转录消息 - 用于显示对话内容
                val transcriptions = transcriptionsState.transcriptions.value
                if (transcriptions.isNotEmpty()) {
                    val lastTranscription = transcriptions.last()
                    // 如果不是本地用户的消息，说明是Agent在说话，添加消息
                    if (lastTranscription.identity != room.localParticipant.identity) {
                        val agentMessage = com.livekit.voiceassistant.core.ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            text = lastTranscription.transcriptionSegment.text,
                            userId = lastTranscription.identity.toString(),
                            userName = "Agent",
                            timestamp = System.currentTimeMillis(),
                            type = com.livekit.voiceassistant.core.ChatMessageType.TEXT,
                            isLocal = false
                        )
                        // 避免重复添加相同消息
                        if (messages.isEmpty() || messages.last().text != agentMessage.text) {
                            messages = messages + agentMessage
                        }
                    }
                }
            } else {
                // 未连接时使用默认波浪效果
                agentAmplitude = 0f
                userAmplitude = defaultWaveAnimation
            }
            
            kotlinx.coroutines.delay(50) // 50ms更新一次，更实时
        }
    }
    
    // 聊天界面布局 - 使用WindowInsets正确处理键盘
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorResource(id = R.color.kimi_bg_top),
                        colorResource(id = R.color.kimi_bg_middle),
                        colorResource(id = R.color.kimi_bg_bottom)
                    )
                )
            )
    ) {
        // 背景：MyWaveView.java 作为最底层虚化背景
        AndroidView(
            factory = { context ->
                MyWaveView(context).apply {
                    // 设置波纹类型：圆形波纹显示，线性波纹显示
                    setWaveType(true, true)
                    
                    // 分别设置Agent和用户振幅
                    setAgentAmplitude(agentAmplitude)
                    setUserAmplitude(userAmplitude)
                    
                    // 启动波纹动画
                    startWaveAnimation()
                }
            },
            update = { view ->
                // 实时更新振幅 - 分别控制圆形和线性波纹
                view.setAgentAmplitude(agentAmplitude)  // 圆形波纹跟Agent语音同步
                view.setUserAmplitude(userAmplitude)    // 线性波纹跟用户语音同步
                
                // 保持动画运行
                if (!view.isActive()) {
                    view.startWaveAnimation()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f)) // 更虚化
        )
        
        // 顶部状态条 - 固定在状态栏下方，不受键盘影响
        ConnectionStatusView(
            connectionState = when {
                isConnecting -> RTCConnectionState.CONNECTING
                isConnected -> RTCConnectionState.CONNECTED
                else -> RTCConnectionState.DISCONNECTED
            },
            serverUrl = url,
            showDetails = false,
            onBackClick = onBack,
            onConnectClick = {
                // 连接服务器
                if (!isConnected) {
                    coroutineScope.launch {
                        try {
                            room.connect(url, token)
                            isConnected = true
                            println("✅ 原始自定义波纹: 正在连接...")
                        } catch (e: Exception) {
                            println("❌ 原始自定义波纹: 连接失败: ${e.message}")
                        }
                    }
                }
            },
            onDisconnectClick = {
                // 断开连接
                if (isConnected) {
                    try {
                        room.disconnect()
                        isConnected = false
                        agentAmplitude = 0f
                        userAmplitude = 0f
                        messages = emptyList()
                        println("✅ 原始自定义波纹: 已断开连接")
                    } catch (e: Exception) {
                        println("❌ 原始自定义波纹: 断开连接失败: ${e.message}")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .statusBarsPadding() // 状态栏padding
                .align(Alignment.TopCenter) // 固定在顶部，不受键盘影响
        )
        
        // 主内容区域 - 使用WindowInsets正确处理键盘
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp) // 为顶部状态栏留出空间
                .windowInsetsPadding(WindowInsets.ime) // 键盘弹出时自动调整
        ) {
            // 消息列表区域 - 使用weight占据剩余空间，键盘弹出时自动缩小
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // 使用现有的ChatLog组件 - 跟KimiChatScreen.kt一样
                com.livekit.voiceassistant.ui.ChatLog(
                    room = room,
                    transcriptions = transcriptionsState.transcriptions.value,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_white_alpha_10)
                        )
                )
            }

                        // 底部：美化圆形连接/断开按钮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 半透明悬浮连接按钮 - 使用IconButton去掉六边形形状
                            IconButton(
                                onClick = {
                                    if (isConnected) {
                                        // 安全断开连接
                                        coroutineScope.launch {
                                            try {
                                                // 先停止音频检测
                                                agentAmplitude = 0f
                                                userAmplitude = 0f
                                                messages = emptyList()
                                                
                                                // 然后断开连接
                                                room.disconnect()
                                                isConnected = false
                                                isConnecting = false
                                                println("✅ 原始自定义波纹: 已断开连接")
                                            } catch (e: Exception) {
                                                println("❌ 原始自定义波纹: 断开连接失败: ${e.message}")
                                                // 即使断开失败，也重置状态
                                                isConnected = false
                                                isConnecting = false
                                                agentAmplitude = 0f
                                                userAmplitude = 0f
                                                messages = emptyList()
                                            }
                                        }
                                    } else if (!isConnecting) {
                                        // 连接服务器
                                        isConnecting = true
                                        coroutineScope.launch {
                                            try {
                                                println("✅ 原始自定义波纹: 正在连接... URL: $url, Token: ${token.take(20)}...")
                                                room.connect(url, token)
                                                isConnected = true
                                                isConnecting = false
                                                println("✅ 原始自定义波纹: 连接成功")
                                            } catch (e: Exception) {
                                                println("❌ 原始自定义波纹: 连接失败: ${e.message}")
                                                isConnected = false
                                                isConnecting = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(80.dp) // 适中的按钮尺寸
                                    .background(
                                        color = when {
                                            isConnecting -> Color(0x80FFA500) // 半透明橙色 - 连接中
                                            isConnected -> Color(0x80FF6B6B) // 半透明红色 - 已连接
                                            else -> Color(0x804ECDC4) // 半透明青色 - 未连接
                                        },
                                        shape = CircleShape
                                    )
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = CircleShape,
                                        ambientColor = when {
                                            isConnecting -> Color(0x80FFA500)
                                            isConnected -> Color(0x80FF6B6B)
                                            else -> Color(0x804ECDC4)
                                        }
                                    )
                            ) {
                                // 只显示图标，不显示文字
                                Icon(
                                    imageVector = when {
                                        isConnecting -> Icons.Default.Refresh // 连接中显示刷新图标
                                        isConnected -> Icons.Default.Stop // 已连接显示停止图标
                                        else -> Icons.Default.PlayArrow // 未连接显示播放图标
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.White
                                )
                            }
                        }
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "原始自定义波纹")
@Composable
fun WaveScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 预览用的简化版本
        OriginalCustomWaveContentPreview()
    }
}

@Composable
private fun OriginalCustomWaveContentPreview() {
    // 状态管理
    var isConnected by remember { mutableStateOf(true) }
    var agentAmplitude by remember { mutableStateOf(1.0f) }
    var userAmplitude by remember { mutableStateOf(1.0f) }
    
    // 默认波浪起伏效果
    var defaultWaveAnimation by remember { mutableStateOf(0.7f) }
    
    // 聊天界面布局 - 使用WindowInsets正确处理键盘
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorResource(id = R.color.kimi_bg_top),
                        colorResource(id = R.color.kimi_bg_middle),
                        colorResource(id = R.color.kimi_bg_bottom)
                    )
                )
            )
    ) {
        // 背景：MyWaveView.java 作为最底层虚化背景
        AndroidView(
            factory = { context ->
                MyWaveView(context).apply {
                    // 设置波纹类型：圆形波纹显示，线性波纹显示
                    setWaveType(true, true)
                    
                    // 分别设置Agent和用户振幅
                    setAgentAmplitude(agentAmplitude)
                    setUserAmplitude(userAmplitude)
                    
                    // 启动波纹动画
                    startWaveAnimation()
                }
            },
            update = { view ->
                // 实时更新振幅 - 分别控制圆形和线性波纹
                view.setAgentAmplitude(agentAmplitude)  // 圆形波纹跟Agent语音同步
                view.setUserAmplitude(userAmplitude)    // 线性波纹跟用户语音同步
                
                // 保持动画运行
                if (!view.isActive()) {
                    view.startWaveAnimation()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.1f))
        )
        
        // 顶部状态条
        ConnectionStatusView(
            connectionState = RTCConnectionState.CONNECTED,
            serverUrl = "wss://demo.livekit.cloud",
            showDetails = false,
            onBackClick = { },
            onConnectClick = { },
            onDisconnectClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        )
        
        // 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp)
        ) {
            // 消息列表区域
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // 模拟消息列表
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_white_alpha_10)
                        )
                ) {
                    Text(
                        text = "消息列表区域\n(预览模式)",
                        color = colorResource(id = R.color.kimi_text_primary),
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // 底部：圆形连接/断开按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // 半透明悬浮连接按钮 - 使用IconButton去掉六边形形状
                IconButton(
                    onClick = { /* 预览中不执行 */ },
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color(0x80FF6B6B), // 半透明红色
                            shape = CircleShape
                        )
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x80FF6B6B)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
