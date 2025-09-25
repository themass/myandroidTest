package com.livekit.voiceassistant.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import com.livekit.voiceassistant.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kimi全局圆形按钮组件
 * 用于开始/结束对话等主要操作
 * 支持动画效果和状态切换
 */
@Composable
fun KimiCircleButton(
    isActive: Boolean = false,
    onClick: () -> Unit,
    activeIcon: ImageVector = Icons.Default.Stop,
    inactiveIcon: ImageVector = Icons.Default.PlayArrow,
    activeText: String = stringResource(id = R.string.button_end_conversation),
    inactiveText: String = stringResource(id = R.string.button_start_conversation),
    size: Dp = 60.dp, // 与原来语音对话页保持一致
    showText: Boolean = true, // 新增参数控制是否显示文字
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "button_scale"
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = tween(300),
        label = "icon_scale"
    )
    
    // 透明度动画 - 半透明到不透明
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.6f, // 默认更透明，点击时变不透明
        animationSpec = tween(200),
        label = "button_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp), // 减少间距，让按钮和文字更紧凑
        modifier = modifier.height(80.dp) // 限制整体高度不超过80dp，留有余量
    ) {
        // 圆形按钮 - 纯色圆形，去掉边框和阴影避免六边形效果
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha) // 半透明效果
                .clip(CircleShape)
                // 移除border - 可能造成六边形视觉效果
                // 移除shadow - 可能造成六边形阴影效果
                .background(
                    color = if (isActive) 
                        colorResource(id = R.color.kimi_button_danger)
                    else 
                        colorResource(id = R.color.kimi_button_primary),
                    shape = CircleShape
                )
                .clickable {
                    isPressed = true
                    onClick()
                    // 延迟重置按压状态，保持动画效果
                    GlobalScope.launch {
                        delay(200)
                        isPressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // 恢复图标显示
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = if (isActive) activeText else inactiveText,
                modifier = Modifier
                    .size(24.dp) // 与原来KimiControlButton的图标大小一致
                    .scale(iconScale),
                tint = colorResource(id = R.color.kimi_white_full)
            )
        }
        
        // 按钮文字 - 可选显示
        if (showText) {
            Text(
                text = if (isActive) activeText else inactiveText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isActive) 
                    colorResource(id = R.color.kimi_button_danger)
                else 
                    colorResource(id = R.color.kimi_button_primary)
            )
        }
    }
}

// 预设的开始/结束对话圆形按钮
@Composable
fun KimiConversationCircleButton(
    isConversationActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp, // 与原来语音对话页保持一致
    showText: Boolean = true // 新增参数控制是否显示文字
) {
    KimiCircleButton(
        isActive = isConversationActive,
        onClick = onClick,
        activeIcon = Icons.Default.Stop,
        inactiveIcon = Icons.Default.PlayArrow,
        activeText = stringResource(id = R.string.button_end_conversation),
        inactiveText = stringResource(id = R.string.button_start_conversation),
        size = size,
        showText = showText,
        modifier = modifier
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "未激活状态")
@Composable
fun KimiCircleButtonInactivePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiCircleButton(
            isActive = false,
            onClick = { }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "激活状态")
@Composable
fun KimiCircleButtonActivePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiCircleButton(
            isActive = true,
            onClick = { }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "对话按钮")
@Composable
fun KimiConversationCircleButtonPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        KimiConversationCircleButton(
            isConversationActive = false,
            onClick = { }
        )
    }
}
