package com.livekit.voiceassistant.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.core.RTCConnectionState

/**
 * Kimi全局连接状态组件
 * 显示RTC连接状态，支持多种RTC服务
 * 可在所有页面顶部使用
 */

// 兼容性别名，逐步迁移到新的状态类型
typealias KimiConnectionState = RTCConnectionState

@Composable
fun KimiConnectionStatusView(
    connectionState: KimiConnectionState,
    serverUrl: String? = null,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onConnectClick: (() -> Unit)? = null,
    onDisconnectClick: (() -> Unit)? = null
) {
    // 连接中的脉冲动画
    val pulseScale by animateFloatAsState(
        targetValue = if (connectionState == KimiConnectionState.CONNECTING || 
                         connectionState == KimiConnectionState.RECONNECTING) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // 清晰的状态背景 - 不虚化，固定高度
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // 固定高度，确保所有状态一致
            .background(
                color = getStatusColor(connectionState).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：后退按钮
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_white_alpha_20),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colorResource(id = R.color.kimi_text_primary),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            
            // 中间：状态指示器和文字
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示器圆点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(if (connectionState == KimiConnectionState.CONNECTING || 
                                   connectionState == KimiConnectionState.RECONNECTING) pulseScale else 1f)
                        .background(
                            color = getStatusColor(connectionState),
                            shape = CircleShape
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 状态文字
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = getStatusText(connectionState),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(id = R.color.kimi_text_primary),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 右侧：连接/断链按钮 - 固定高度避免跳动
            Box(
                modifier = Modifier
                    .width(60.dp) // 固定宽度
                    .height(32.dp), // 固定高度，与按钮高度一致
                contentAlignment = Alignment.Center
            ) {
                when (connectionState) {
                    KimiConnectionState.DISCONNECTED, KimiConnectionState.ERROR -> {
                        // 未连接状态 - 显示连接按钮
                        if (onConnectClick != null) {
                            Button(
                                onClick = onConnectClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.kimi_green_success)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp) // 固定按钮高度
                            ) {
                                Text(
                                    text = "连接",
                                    fontSize = 12.sp,
                                    color = colorResource(id = R.color.kimi_white_full)
                                )
                            }
                        }
                    }
                    KimiConnectionState.CONNECTED -> {
                        // 已连接状态 - 显示断链按钮
                        if (onDisconnectClick != null) {
                            Button(
                                onClick = onDisconnectClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.kimi_red_danger)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp) // 固定按钮高度
                            ) {
                                Text(
                                    text = "断链",
                                    fontSize = 12.sp,
                                    color = colorResource(id = R.color.kimi_white_full)
                                )
                            }
                        }
                    }
                    KimiConnectionState.CONNECTING, KimiConnectionState.RECONNECTING -> {
                        // 连接中状态 - 显示加载动画，保持相同高度
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(32.dp) // 与按钮相同高度
                        ) {
                            Text(
                                text = "连接中...",
                                fontSize = 10.sp,
                                color = colorResource(id = R.color.kimi_text_hint)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = colorResource(id = R.color.kimi_yellow_warning),
                                strokeWidth = 1.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getStatusColor(state: KimiConnectionState) = when (state) {
    KimiConnectionState.CONNECTED -> colorResource(id = R.color.kimi_green_success)
    KimiConnectionState.CONNECTING, 
    KimiConnectionState.RECONNECTING -> colorResource(id = R.color.kimi_yellow_warning)
    KimiConnectionState.ERROR -> colorResource(id = R.color.kimi_red_danger)
    KimiConnectionState.DISCONNECTED -> colorResource(id = R.color.kimi_button_inactive)
}

@Composable
private fun getStatusText(state: KimiConnectionState) = when (state) {
    KimiConnectionState.CONNECTED -> stringResource(id = R.string.status_connected_to_server)
    KimiConnectionState.CONNECTING -> stringResource(id = R.string.status_connecting_to_server)
    KimiConnectionState.RECONNECTING -> stringResource(id = R.string.status_reconnecting_to_server)
    KimiConnectionState.ERROR -> "连接错误"
    KimiConnectionState.DISCONNECTED -> stringResource(id = R.string.status_not_connected)
}

// 简化版本 - 只显示基本状态
@Composable
fun KimiSimpleConnectionStatus(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    KimiConnectionStatusView(
        connectionState = if (isConnected) KimiConnectionState.CONNECTED else KimiConnectionState.DISCONNECTED,
        modifier = modifier,
        showDetails = false
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "已连接状态")
@Composable
fun KimiConnectionStatusConnectedPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiConnectionStatusView(
            connectionState = KimiConnectionState.CONNECTED,
            serverUrl = "wss://testvoice-b8y35yvd.livekit.cloud",
            showDetails = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "连接中状态")
@Composable
fun KimiConnectionStatusConnectingPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiConnectionStatusView(
            connectionState = KimiConnectionState.CONNECTING,
            serverUrl = "wss://testvoice-b8y35yvd.livekit.cloud",
            showDetails = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "未连接状态")
@Composable
fun KimiConnectionStatusDisconnectedPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiConnectionStatusView(
            connectionState = KimiConnectionState.DISCONNECTED
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "简化版状态")
@Composable
fun KimiSimpleConnectionStatusPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiSimpleConnectionStatus(
            isConnected = true
        )
    }
}
