package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.livekit.voiceassistant.R
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * 音频同步的 AI 头像组件
 * 支持用户说话时的波纹效果和 AI 说话时的跳动效果
 */
@Composable
fun AudioSyncAvatarIcon(
    isUserSpeaking: Boolean = false,
    isAISpeaking: Boolean = false,
    isConversationActive: Boolean = false,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f // 音频强度 0.0 - 1.0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_animation")
    
    // 基础缩放动画（始终存在的呼吸效果）
    val baseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "base_scale"
    )
    
    // 用户说话时的脉冲缩放
    val userSpeakingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isUserSpeaking) 1.2f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isUserSpeaking) 300 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "user_speaking_scale"
    )
    
    // AI 说话时的快速跳动
    val aiSpeakingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isAISpeaking) 1.15f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isAISpeaking) 150 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_speaking_scale"
    )
    
    // 波纹动画状态
    var ripplePhase by remember { mutableStateOf(0f) }
    
    // 启动波纹动画
    LaunchedEffect(isUserSpeaking) {
        if (isUserSpeaking) {
            while (isUserSpeaking) {
                ripplePhase += 0.1f
                if (ripplePhase > 2 * Math.PI) {
                    ripplePhase = 0f
                }
                delay(16) // ~60fps
            }
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 用户说话时的波纹效果
        if (isUserSpeaking) {
            repeat(3) { index ->
                val rippleDelay = index * 0.3f
                val rippleScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 2.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, delayMillis = (rippleDelay * 1000).toInt()),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ripple_$index"
                )
                
                val rippleAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, delayMillis = (rippleDelay * 1000).toInt()),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ripple_alpha_$index"
                )
                
                Box(
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.kimi_avatar_size_md))
                        .scale(rippleScale)
                        .clip(CircleShape)
                        .background(
                            colorResource(id = R.color.kimi_button_danger).copy(alpha = rippleAlpha)
                        )
                )
            }
        }
        
        // AI 说话时的光晕效果
        if (isAISpeaking) {
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ai_glow"
            )
            
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ai_glow_alpha"
            )
            
            Box(
                modifier = Modifier
                    .size(dimensionResource(id = R.dimen.kimi_avatar_size_md))
                    .scale(glowScale)
                    .clip(CircleShape)
                    .background(
                        colorResource(id = R.color.kimi_button_primary).copy(alpha = glowAlpha)
                    )
            )
        }
        
        // 主头像
        Box(
            modifier = Modifier
                .size(dimensionResource(id = R.dimen.kimi_avatar_size_md))
                .scale(baseScale * userSpeakingScale * aiSpeakingScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorResource(id = R.color.kimi_secondary),
                            colorResource(id = R.color.kimi_primary),
                            colorResource(id = R.color.kimi_accent)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // 根据状态显示不同图标
            val iconVector: ImageVector = when {
                isUserSpeaking -> Icons.Default.Mic
                isAISpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                isConversationActive -> Icons.Default.Phone
                else -> Icons.Default.Mic
            }
            
            val iconColor = when {
                isUserSpeaking -> colorResource(id = R.color.kimi_button_danger)
                isAISpeaking -> colorResource(id = R.color.kimi_button_primary)
                else -> colorResource(id = R.color.kimi_text_primary)
            }
            
            Icon(
                imageVector = iconVector,
                contentDescription = when {
                    isUserSpeaking -> "用户正在说话"
                    isAISpeaking -> "AI 正在说话"
                    isConversationActive -> "通话中"
                    else -> "AI 语音助手"
                },
                modifier = Modifier.size(dimensionResource(id = R.dimen.kimi_icon_size_xxl)),
                tint = iconColor
            )
        }
    }
}

/**
 * 音频同步的语音可视化条
 * 根据音频强度实时调整高度和颜色
 */
@Composable
fun AudioSyncVoiceBars(
    isActive: Boolean = false,
    isUserSpeaking: Boolean = false,
    isAISpeaking: Boolean = false,
    audioLevels: FloatArray = floatArrayOf(), // 实时音频数据
    barCount: Int = 8,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_bars")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.kimi_padding_sm)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(barCount) { index ->
            // 使用实际音频数据或模拟数据
            val audioLevel = if (audioLevels.isNotEmpty() && index < audioLevels.size) {
                audioLevels[index]
            } else {
                // 模拟音频数据
                if (isActive || isUserSpeaking || isAISpeaking) {
                    Random.nextFloat()
                } else {
                    0.1f
                }
            }
            
            // 根据音频强度计算高度
            val targetHeight = when {
                isUserSpeaking -> 20f + audioLevel * 30f
                isAISpeaking -> 15f + audioLevel * 25f
                isActive -> 10f + audioLevel * 20f
                else -> 5f + audioLevel * 10f
            }
            
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = targetHeight,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = when {
                            isUserSpeaking -> 100
                            isAISpeaking -> 150
                            else -> 300
                        }
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_height_$index"
            )
            
            // 根据状态选择颜色
            val barColor = when {
                isUserSpeaking -> colorResource(id = R.color.kimi_button_danger)
                isAISpeaking -> colorResource(id = R.color.kimi_button_primary)
                isActive -> colorResource(id = R.color.kimi_white_alpha_70)
                else -> colorResource(id = R.color.kimi_white_alpha_30)
            }
            
            Box(
                modifier = Modifier
                    .width(dimensionResource(id = R.dimen.kimi_visualizer_bar_width))
                    .height(animatedHeight.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(dimensionResource(id = R.dimen.kimi_radius_xs)))
                    .background(barColor)
            )
        }
    }
}

/**
 * 音频同步的波形显示
 * 显示更复杂的波形可视化效果
 */
@Composable
fun AudioSyncWaveform(
    isActive: Boolean = false,
    isUserSpeaking: Boolean = false,
    isAISpeaking: Boolean = false,
    audioData: FloatArray = floatArrayOf(), // 音频波形数据
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    // 时间偏移用于波形滚动效果
    val timeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "time_offset"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                color = colorResource(id = R.color.kimi_white_alpha_10),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(dimensionResource(id = R.dimen.kimi_radius_md))
            )
            .padding(dimensionResource(id = R.dimen.kimi_padding_md)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawWaveform(
                isActive = isActive,
                isUserSpeaking = isUserSpeaking,
                isAISpeaking = isAISpeaking,
                timeOffset = timeOffset,
                audioData = audioData
            )
        }
    }
}

private fun DrawScope.drawWaveform(
    isActive: Boolean,
    isUserSpeaking: Boolean,
    isAISpeaking: Boolean,
    timeOffset: Float,
    audioData: FloatArray
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2
    
    // 波形颜色
    val waveColor = when {
        isUserSpeaking -> Color(0xFFE74C3C) // 红色
        isAISpeaking -> Color(0xFF6A4C93) // 主色调
        isActive -> Color(0x80FFFFFF) // 半透明白色
        else -> Color(0x40FFFFFF) // 更透明的白色
    }
    
    // 绘制波形
    val points = 50
    val stepX = width / points
    
    for (i in 0 until points - 1) {
        val x1 = i * stepX
        val x2 = (i + 1) * stepX
        
        // 使用实际音频数据或生成模拟波形
        val amplitude = when {
            isUserSpeaking -> height * 0.3f
            isAISpeaking -> height * 0.25f
            isActive -> height * 0.15f
            else -> height * 0.05f
        }
        
        val frequency = if (isUserSpeaking) 0.02f else if (isAISpeaking) 0.015f else 0.01f
        
        val y1 = centerY + sin((x1 * frequency + timeOffset)) * amplitude
        val y2 = centerY + sin((x2 * frequency + timeOffset)) * amplitude
        
        // 绘制线段
        drawLine(
            color = waveColor,
            start = androidx.compose.ui.geometry.Offset(x1, y1),
            end = androidx.compose.ui.geometry.Offset(x2, y2),
            strokeWidth = if (isUserSpeaking || isAISpeaking) 3.dp.toPx() else 2.dp.toPx()
        )
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "默认状态")
@Composable
fun AudioSyncAvatarIconPreview() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncAvatarIcon(
            isUserSpeaking = false,
            isAISpeaking = false,
            isConversationActive = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "用户说话")
@Composable
fun AudioSyncAvatarIconUserSpeakingPreview() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncAvatarIcon(
            isUserSpeaking = true,
            isAISpeaking = false,
            isConversationActive = true,
            audioLevel = 0.8f
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "AI说话")
@Composable
fun AudioSyncAvatarIconAISpeakingPreview() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncAvatarIcon(
            isUserSpeaking = false,
            isAISpeaking = true,
            isConversationActive = true,
            audioLevel = 0.6f
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "语音条-默认")
@Composable
fun AudioSyncVoiceBarsPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncVoiceBars(
            isActive = false,
            isUserSpeaking = false,
            isAISpeaking = false,
            barCount = 8
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "语音条-用户说话")
@Composable
fun AudioSyncVoiceBarsUserSpeakingPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncVoiceBars(
            isActive = true,
            isUserSpeaking = true,
            isAISpeaking = false,
            barCount = 8
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "波形显示")
@Composable
fun AudioSyncWaveformPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AudioSyncWaveform(
            isActive = true,
            isUserSpeaking = false,
            isAISpeaking = true
        )
    }
}
