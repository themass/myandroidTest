package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.util.flow
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay

/**
 * Kimi主页面 - 真正的连接和状态页面
 * 负责LiveKit连接管理和状态显示
 * 提供到聊天页面和语音页面的导航
 */
@OptIn(Beta::class)
@Composable
fun MainScreen(
    url: String,
    token: String,
    onNavigateToChat: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToWaveDemo: () -> Unit = {},
    onNavigateToOldWave: () -> Unit = {}, // 新增旧版波纹导航
    onNavigateToOriginalCustomWave: () -> Unit = {} // 新增原始自定义波纹导航
) {
    var connectionAttempted by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.05f else 1.0f,
        animationSpec = tween(1000),
        label = "main_scale"
    )

    // 使用真实LiveKit连接的主页面
    RoomScope(
        url = url,
        token = token,
        audio = true,
        connect = connectionAttempted
    ) { room ->
        var roomConnectionState by remember { mutableStateOf(io.livekit.android.room.ConnectionState.DISCONNECTED) }
        
        // 监听连接尝试，模拟连接过程
        LaunchedEffect(connectionAttempted) {
            if (connectionAttempted) {
                roomConnectionState = io.livekit.android.room.ConnectionState.CONNECTING
                delay(2000)
                roomConnectionState = io.livekit.android.room.ConnectionState.CONNECTED
                isAnimating = true
            }
        }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensionResource(id = R.dimen.kimi_padding_xl)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
            // 顶部标题和状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.kimi_title),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(id = R.color.kimi_text_primary),
                    modifier = Modifier.scale(scale)
                )

                Text(
                    text = stringResource(id = R.string.voice_assistant_title),
                    fontSize = 20.sp,
                    color = colorResource(id = R.color.kimi_text_primary)
                )

                // 全局连接状态组件 - 使用真实连接状态
                ConnectionStatusView(
                    connectionState = when (roomConnectionState) {
                        io.livekit.android.room.ConnectionState.CONNECTED -> RTCConnectionState.CONNECTED
                        io.livekit.android.room.ConnectionState.CONNECTING -> RTCConnectionState.CONNECTING
                        io.livekit.android.room.ConnectionState.RECONNECTING -> RTCConnectionState.RECONNECTING
                        else -> RTCConnectionState.DISCONNECTED
                    },
                    serverUrl = url,
                    showDetails = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 中央圆形连接按钮 - 控制真实LiveKit连接
            KimiConversationCircleButton(
                isConversationActive = roomConnectionState == io.livekit.android.room.ConnectionState.CONNECTED,
                onClick = {
                    if (roomConnectionState == io.livekit.android.room.ConnectionState.CONNECTED) {
                        // 断开连接
                        room.disconnect()
                        connectionAttempted = false
                    } else {
                        // 开始连接
                        connectionAttempted = true
                    }
                },
                size = 60.dp, // 与原来语音对话页保持一致
                modifier = Modifier.scale(scale)
            )

            // 导航按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (roomConnectionState == io.livekit.android.room.ConnectionState.CONNECTED) 
                        stringResource(id = R.string.nav_select_mode)
                    else 
                        stringResource(id = R.string.nav_connect_first),
                    fontSize = 16.sp,
                    color = if (roomConnectionState == io.livekit.android.room.ConnectionState.CONNECTED) 
                        colorResource(id = R.color.kimi_text_primary)
                    else 
                        colorResource(id = R.color.kimi_text_hint),
                    textAlign = TextAlign.Center
                )

                // 第一行按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onNavigateToChat,
                        enabled = true, // 任何状态都可以点击
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.kimi_secondary),
                            disabledContainerColor = colorResource(id = R.color.kimi_button_inactive)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.button_message_list))
                    }
                    
                    Button(
                        onClick = onNavigateToVoice,
                        enabled = true, // 任何状态都可以点击
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.kimi_accent),
                            disabledContainerColor = colorResource(id = R.color.kimi_button_inactive)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.button_voice_chat))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 第二行按钮 - 波纹演示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToWaveDemo,
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.kimi_primary),
                            disabledContainerColor = colorResource(id = R.color.kimi_button_inactive)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新版波纹", fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = onNavigateToOldWave,
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.kimi_accent),
                            disabledContainerColor = colorResource(id = R.color.kimi_button_inactive)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("旧版波纹", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 第三行按钮 - 原始自定义波纹
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToOriginalCustomWave,
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2BCEFF),
                            disabledContainerColor = colorResource(id = R.color.kimi_button_inactive)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("原始自定义波纹", fontSize = 14.sp)
                    }
                }
            }

            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E)
@Composable
fun MainScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C1810),
                        Color(0xFF1A0B2E),
                        Color(0xFF0F0511)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Kimi 主页面\n连接管理 + 导航\n真实LiveKit连接",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}