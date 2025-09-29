package com.livekit.voiceassistant.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Kimi语音录音覆盖界面 - 微信风格
 * 正确的实现：按住说话时显示，松开发送，取消和转文字功能
 */
@Composable
fun KimiVoiceRecordingOverlay(
    isVisible: Boolean = false,
    isRecording: Boolean = false,
    onCancel: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onConvertToText: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    // 音频振幅状态 - 用于频谱动画
    var audioAmplitude by remember { mutableStateOf(0f) }
    
    // 使用真实的录音状态来控制音频振幅
    LaunchedEffect(isRecording) {
        if (isRecording) {
            // 录音时显示活跃的音频振幅
            audioAmplitude = 0.7f
        } else {
            audioAmplitude = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // 半透明遮罩
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部空白区域
            Spacer(modifier = Modifier.weight(1f))
            
            // 中央音频可视化区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // 绿色语音气泡 - 微信风格
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(80.dp)
                        .background(
                            color = Color(0xFF4CAF50), // 绿色
                            shape = RoundedCornerShape(40.dp) // 胶囊形状
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 音频频谱条形图
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(8) { index ->
                            val height = if (isRecording) {
                                val baseHeight = 0.3f
                                val amplitudeEffect = audioAmplitude * 0.7f
                                val randomVariation = Random.nextFloat() * 0.2f
                                val centerBoost = if (index in 3..4) 0.3f else 0f
                                (baseHeight + amplitudeEffect + randomVariation + centerBoost).coerceIn(0.1f, 1f)
                            } else {
                                0.2f
                            }
                            
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height((height * 48).dp)
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(3.dp) // 小圆角条形
                                    )
                            )
                        }
                    }
                }
            }
            
            // 底部控制栏 - 微信风格
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                // 半透明灰色控制栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            color = Color(0xFFE0E0E0).copy(alpha = 0.9f), // 浅灰色
                            shape = RoundedCornerShape(25.dp) // 圆角矩形
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：取消按钮 (X图标)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = Color(0xFF757575), // 深灰色
                                    shape = CircleShape
                                )
                                .clickable { onCancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "×",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 中间：松开发送文字提示
                        Text(
                            text = "松开 发送",
                            color = Color(0xFF424242), // 深灰色文字
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )

                        // 右侧：转文字按钮 (文图标)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = Color(0xFF757575), // 深灰色
                                    shape = CircleShape
                                )
                                .clickable { onConvertToText() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "文",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "录音覆盖界面")
@Composable
fun KimiVoiceRecordingOverlayPreview() {
    KimiVoiceRecordingOverlay(
        isVisible = true,
        isRecording = true
    )
}
