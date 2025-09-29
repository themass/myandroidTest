package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.*
import com.livekit.voiceassistant.core.RTCConnectionState

/**
 * Kimi语音页面 - 简化的纯语音交互界面
 * 专注于语音对话功能
 */
@Composable
fun VoiceScreen(
    url: String,
    token: String,
    onBack: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1.0f,
        animationSpec = tween(1000),
        label = "voice_scale"
    )

    // 按照图片重新设计的语音界面布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // 白色背景，匹配图片
    ) {
        // 1. 顶部状态栏
        ConnectionStatusView(
            connectionState = if (isConnected) RTCConnectionState.CONNECTED else RTCConnectionState.DISCONNECTED,
            serverUrl = url,
            showDetails = false,
            onBackClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // 2. 标题栏 - "Kimi 语音"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 标题
            Text(
                text = "Kimi 语音",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 右侧占位，保持标题居中
            Box(modifier = Modifier.size(48.dp))
        }

        // 3. 中央区域 - 大圆形按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // 中央大圆形按钮 - 200dp大小，匹配图片
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // 圆形背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF8E44AD),
                                    Color(0xFF6A4C93)
                                )
                            ),
                            shape = CircleShape
                        )
                        .clickable {
                            isConnected = !isConnected
                            isRecording = !isRecording
                        }
                )
                
                // 麦克风图标
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音对话",
                    modifier = Modifier.size(60.dp),
                    tint = Color.White
                )
                
                // 底部小点指示器
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (index == 2) 
                                        Color.White 
                                    else 
                                        Color.White.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // 4. 状态文字
        Text(
            text = if (isConnected) 
                "正在进行语音对话..." 
            else 
                "点击开始语音对话",
            fontSize = 16.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // 5. 底部按钮区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧麦克风按钮
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = Color(0xFF8E44AD),
                        shape = CircleShape
                    )
                    .clickable { isRecording = !isRecording },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "键盘",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 中间开始/结束按钮
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = if (isConnected) Color(0xFFE74C3C) else Color(0xFF27AE60),
                        shape = CircleShape
                    )
                    .clickable { 
                        isConnected = !isConnected
                        if (!isConnected) isRecording = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CallEnd else Icons.Default.PlayArrow,
                    contentDescription = if (isConnected) "结束" else "开始",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 右侧发送按钮
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = Color(0xFF8E44AD),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun VoiceScreenPreview() {
    VoiceScreen(
        url = "wss://example.com",
        token = "test_token",
        onBack = {}
    )
}