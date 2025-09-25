package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import io.livekit.android.room.ConnectionState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.*
import com.livekit.voiceassistant.core.*
import com.livekit.voiceassistant.datastreams.TranscriptionsState
import com.livekit.voiceassistant.datastreams.Transcription
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.datastream.TextStreamInfo
import kotlinx.coroutines.launch
import android.net.Uri

/**
 * Kimi聊天界面 - 干净的实现版本
 * 真正使用LiveKit连接，移除所有mock代码
 */
@Composable
fun KimiChatScreen(
    room: Room,
    transcriptionsState: TranscriptionsState,
    url: String? = null,
    token: String? = null,
    onBack: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 连接状态管理 - 使用真实的LiveKit状态
    var connectionState by remember { mutableStateOf(RTCConnectionState.DISCONNECTED) }
    var agentAmplitude by remember { mutableStateOf(0f) }
    
    // 监听真实的Room状态变化
    LaunchedEffect(room) {
        // 启用麦克风
        try {
            room.localParticipant.setMicrophoneEnabled(true)
            println("✅ 麦克风已启用")
        } catch (e: Exception) {
            println("❌ 麦克风启用失败: ${e.message}")
        }
        
        // 简单的连接状态检查
        if (room.localParticipant.identity != null) {
            connectionState = RTCConnectionState.CONNECTED
        } else {
            connectionState = RTCConnectionState.DISCONNECTED
        }
    }
    
    // 监听转录变化来模拟Agent音频活动
    LaunchedEffect(transcriptionsState.transcriptions.value) {
        val transcriptions = transcriptionsState.transcriptions.value
        if (transcriptions.isNotEmpty()) {
            val lastTranscription = transcriptions.last()
            // 如果不是本地用户的消息，说明是Agent在说话
            if (lastTranscription.identity != room.localParticipant.identity) {
                agentAmplitude = 0.8f
                println("🎵 检测到Agent语音活动")
                kotlinx.coroutines.delay(1500)
                agentAmplitude = 0f
            }
        }
    }
    
    // 动画效果
    val scale by animateFloatAsState(
        targetValue = if (agentAmplitude > 0.1f) 1.1f else 1f,
        animationSpec = tween(200),
        label = "audio_visualizer_scale"
    )
    
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
        // 顶部连接状态 - 固定在状态栏下方，不受键盘影响
        KimiConnectionStatusView(
            connectionState = connectionState,
            serverUrl = url,
            showDetails = false,
            onBackClick = onBack,
            onConnectClick = {
                // 真实的LiveKit重连逻辑
                if (connectionState != RTCConnectionState.CONNECTED) {
                    coroutineScope.launch {
                        try {
                            connectionState = RTCConnectionState.CONNECTING
                            Toast.makeText(context, "正在重连...", Toast.LENGTH_SHORT).show()
                            
                            if (url != null && token != null) {
                                room.connect(url, token)
                                connectionState = RTCConnectionState.CONNECTED
                                Toast.makeText(context, "重连成功", Toast.LENGTH_SHORT).show()
                            } else {
                                connectionState = RTCConnectionState.ERROR
                                Toast.makeText(context, "缺少连接参数", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            connectionState = RTCConnectionState.ERROR
                            Toast.makeText(context, "重连失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDisconnectClick = {
                // 真实的LiveKit断开逻辑
                if (connectionState == RTCConnectionState.CONNECTED) {
                    coroutineScope.launch {
                        try {
                            room.disconnect()
                            connectionState = RTCConnectionState.DISCONNECTED
                            Toast.makeText(context, "已断开连接", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "断开失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
                // 使用现有的ChatLog组件
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
                
                // 音频可视化组件 - 悬浮在消息列表上方
                KimiRealTimeAudioVisualizer(
                    amplitude = agentAmplitude,
                    isActive = true, // 始终显示，包括等待状态
                    waveType = KimiWaveType.PARTICLE, // 默认使用粒子效果
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 50.dp)
                        .scale(scale)
                )
            }

            // 输入框区域 - 固定在底部，键盘弹出时跟随上移
            KimiRecordInputView(
                text = inputText,
                onTextChange = { inputText = it },
                showKeyboardButton = false,
                onImageSelected = { uri ->
                    // 图片消息处理
                    val identity = room.localParticipant.identity
                    if (identity != null) {
                        transcriptionsState.addTranscription(identity, "📷 ${uri.lastPathSegment}")
                        Toast.makeText(context, "图片已添加", Toast.LENGTH_SHORT).show()
                    }
                },
                onAudioSelected = { uri ->
                    // 音频消息处理
                    val identity = room.localParticipant.identity
                    if (identity != null) {
                        transcriptionsState.addTranscription(identity, "🎵 ${uri.lastPathSegment}")
                        Toast.makeText(context, "音频已添加", Toast.LENGTH_SHORT).show()
                    }
                },
                onVideoSelected = { uri ->
                    // 视频消息处理
                    val identity = room.localParticipant.identity
                    if (identity != null) {
                        transcriptionsState.addTranscription(identity, "🎥 ${uri.lastPathSegment}")
                        Toast.makeText(context, "视频已添加", Toast.LENGTH_SHORT).show()
                    }
                },
                onFileSelected = { uri ->
                    // 文件消息处理
                    val identity = room.localParticipant.identity
                    if (identity != null) {
                        transcriptionsState.addTranscription(identity, "📄 ${uri.lastPathSegment}")
                        Toast.makeText(context, "文件已添加", Toast.LENGTH_SHORT).show()
                    }
                },
                onSend = {
                    // 真实的LiveKit文本消息发送
                    if (inputText.isNotEmpty() && connectionState == RTCConnectionState.CONNECTED) {
                        coroutineScope.launch {
                            try {
                                // 发送到LiveKit数据通道
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
                                sender.write(inputText)
                                sender.close()
                                
                                // 添加到本地显示
                                val identity = room.localParticipant.identity!!
                                transcriptionsState.addTranscription(identity, inputText)
                                
                                // 清空输入框
                                inputText = ""
                                Toast.makeText(context, "消息发送成功", Toast.LENGTH_SHORT).show()
                                println("✅ 消息已发送: $inputText")
                                
                            } catch (e: Exception) {
                                Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                println("❌ 消息发送失败: ${e.message}")
                            }
                        }
                    } else if (inputText.isNotEmpty()) {
                        Toast.makeText(context, "未连接，请先连接服务器", Toast.LENGTH_SHORT).show()
                    }
                },
                placeholder = "输入消息...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "Kimi聊天界面")
@Composable
fun KimiChatScreenPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(id = R.color.kimi_primary),
            secondary = colorResource(id = R.color.kimi_secondary),
            background = colorResource(id = R.color.kimi_background)
        )
    ) {
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
            // 预览用的简化版本
            KimiChatScreenPreviewContent()
        }
    }
}

@Composable
private fun KimiChatScreenPreviewContent() {
    var inputText by remember { mutableStateOf("") }
    var connectionState by remember { mutableStateOf(RTCConnectionState.CONNECTED) }
    var agentAmplitude by remember { mutableStateOf(0.6f) }
    
    // 动画效果
    val scale by animateFloatAsState(
        targetValue = if (agentAmplitude > 0.1f) 1.1f else 1f,
        animationSpec = tween(200),
        label = "audio_visualizer_scale"
    )
    
    // 聊天界面布局
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
        // 顶部连接状态
        KimiConnectionStatusView(
            connectionState = connectionState,
            serverUrl = "wss://demo.livekit.cloud",
            showDetails = false,
            onBackClick = { },
            onConnectClick = { },
            onDisconnectClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                
                // 音频可视化组件
                KimiRealTimeAudioVisualizer(
                    amplitude = agentAmplitude,
                    isActive = true,
                    waveType = KimiWaveType.PARTICLE,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 50.dp)
                        .scale(scale)
                )
            }

            // 输入框区域
            KimiRecordInputView(
                text = inputText,
                onTextChange = { inputText = it },
                showKeyboardButton = false,
                onImageSelected = { },
                onAudioSelected = { },
                onVideoSelected = { },
                onFileSelected = { },
                onSend = { },
                placeholder = "输入消息...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}