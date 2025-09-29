package com.livekit.voiceassistant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.CustomWaveView
import com.livekit.voiceassistant.component.CustomWaveType
import com.livekit.voiceassistant.datastreams.rememberTranscriptions
import io.livekit.android.compose.local.RoomScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 旧版波纹演示页面 - 按照图片样式设计
 * 简洁的语音对话界面，包含：
 * - 顶部连接状态横幅
 * - 中央麦克风按钮（带波纹光晕）
 * - 底部线性波纹代表用户声音
 * - 对话状态和结束按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
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
            
            // 旧版波纹效果展示
            DemoScreenContent(room, transcriptionsState, url, token, onBack)
        }
    }
}

@Composable
private fun DemoScreenContent(
    room: io.livekit.android.room.Room,
    transcriptionsState: com.livekit.voiceassistant.datastreams.TranscriptionsState,
    url: String,
    token: String,
    onBack: () -> Unit
) {
    // 状态管理
    var isConnected by remember { mutableStateOf(false) }
    var isDialogueActive by remember { mutableStateOf(false) }
    var userAmplitude by remember { mutableStateOf(0f) }
    var agentAmplitude by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    // 启用麦克风
    LaunchedEffect(room) {
        try {
            room.localParticipant.setMicrophoneEnabled(true)
            println("✅ 旧版Demo: 麦克风已启用")
        } catch (e: Exception) {
            println("❌ 旧版Demo: 麦克风启用失败: ${e.message}")
        }
    }
    
    // 监听真实的LiveKit连接状态
    LaunchedEffect(room) {
        if (room.localParticipant.identity != null) {
            isConnected = true
            isDialogueActive = true
        } else {
            isConnected = false
            isDialogueActive = false
        }
    }
    
    // 监听转录变化来检测Agent音频活动
    LaunchedEffect(transcriptionsState.transcriptions.value) {
        val transcriptions = transcriptionsState.transcriptions.value
        if (transcriptions.isNotEmpty()) {
            val lastTranscription = transcriptions.last()
            // 如果不是本地用户的消息，说明是Agent在说话
            if (lastTranscription.identity != room.localParticipant.identity) {
                agentAmplitude = 0.8f
                println("🎵 旧版Demo: 检测到Agent语音活动")
                kotlinx.coroutines.delay(1500)
                agentAmplitude = 0f
            }
        }
    }
    
    // 监听远程参与者来检测用户音频活动
    LaunchedEffect(room.remoteParticipants) {
        val remoteParticipants = room.remoteParticipants
        if (remoteParticipants.isNotEmpty()) {
            // 检查是否有远程参与者在说话
            val hasActiveSpeaker = remoteParticipants.values.any { participant ->
                participant.isSpeaking
            }
            if (hasActiveSpeaker) {
                userAmplitude = 0.7f
                println("🎵 旧版Demo: 检测到远程参与者语音活动")
            } else {
                userAmplitude = 0f
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Black
                )
            }
            
            Text(
                text = "旧版波纹效果演示",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 连接状态横幅
            if (isConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E8)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Green, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "已连接到LiveKit服务器",
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "服务器: wss://testvoice-b8y35yvd...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "未连接",
                    color = Color.Red,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 中央麦克风按钮
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // 波纹光晕效果
                if (isDialogueActive) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                Color(0xFF2BCEFF).copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
                
                // 麦克风按钮
                Button(
                    onClick = { 
                        isDialogueActive = !isDialogueActive
                        if (!isDialogueActive) {
                            userAmplitude = 0f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2BCEFF)
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "麦克风",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            // 对话状态
            if (isDialogueActive) {
                Text(
                    text = "对话进行中",
                    color = Color.Green,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 底部用户线性波纹
            if (isDialogueActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    // 用户线性波纹
                    CustomWaveView(
                        isActive = isDialogueActive,
                        amplitude = userAmplitude,
                        waveType = CustomWaveType.LINEAR,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            
            // 结束对话按钮
            if (isDialogueActive) {
                Button(
                    onClick = { 
                        isDialogueActive = false
                        userAmplitude = 0f
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(60.dp)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "结束对话",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = "结束对话",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                // 连接按钮
                Button(
                    onClick = { 
                        if (isConnected) {
                            // 断开连接
                            try {
                                room.disconnect()
                                isConnected = false
                                isDialogueActive = false
                                userAmplitude = 0f
                                agentAmplitude = 0f
                                println("✅ 旧版Demo: 已断开连接")
                            } catch (e: Exception) {
                                println("❌ 旧版Demo: 断开连接失败: ${e.message}")
                            }
                        } else {
                            // 连接服务器 - 需要在协程中调用
                            coroutineScope.launch {
                                try {
                                    room.connect(url, token)
                                    isConnected = true
                                    isDialogueActive = true
                                    println("✅ 旧版Demo: 正在连接...")
                                } catch (e: Exception) {
                                    println("❌ 旧版Demo: 连接失败: ${e.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color.Red else Color.Green
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isConnected) "断开连接" else "连接服务器",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "旧版波纹演示")
@Composable
fun DemoScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 预览用的简化版本
        DemoScreenContentPreview()
    }
}

@Composable
private fun DemoScreenContentPreview() {
    // 状态管理
    var isConnected by remember { mutableStateOf(true) }
    var isDialogueActive by remember { mutableStateOf(true) }
    var userAmplitude by remember { mutableStateOf(0.7f) }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Black
                )
            }
            
            Text(
                text = "旧版波纹效果演示",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 连接状态横幅
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E8)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Green, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "已连接到LiveKit服务器",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "服务器: wss://testvoice-b8y35yvd...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 中央麦克风按钮
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // 波纹光晕效果
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            Color(0xFF2BCEFF).copy(alpha = 0.3f),
                            CircleShape
                        )
                )
                
                // 麦克风按钮
                Button(
                    onClick = { /* 预览中不执行 */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2BCEFF)
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "麦克风",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            // 对话状态
            Text(
                text = "对话进行中",
                color = Color.Green,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 底部用户线性波纹
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // 用户线性波纹
                CustomWaveView(
                    isActive = true,
                    amplitude = userAmplitude,
                    waveType = CustomWaveType.LINEAR,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.Center)
                )
            }
            
            // 结束对话按钮
            Button(
                onClick = { /* 预览中不执行 */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "结束对话",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = "结束对话",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}