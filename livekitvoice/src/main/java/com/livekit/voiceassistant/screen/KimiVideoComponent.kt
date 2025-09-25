package com.livekit.voiceassistant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R

@Composable
fun KimiVideoComponent(
    isVideoEnabled: Boolean,
    isVideoActive: Boolean,
    onVideoToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(
                width = dimensionResource(id = R.dimen.kimi_video_width),
                height = dimensionResource(id = R.dimen.kimi_video_height)
            )
            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.kimi_video_corner_radius)))
            .background(colorResource(id = R.color.kimi_video_bg))
            .border(
                width = dimensionResource(id = R.dimen.kimi_video_border_width),
                color = colorResource(id = R.color.kimi_video_border),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.kimi_video_corner_radius))
            )
    ) {
        if (isVideoEnabled && isVideoActive) {
            // 视频流显示区域
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 这里应该显示实际的视频流
                // 目前显示占位符
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(id = R.color.kimi_video_placeholder)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "视频流",
                        color = colorResource(id = R.color.kimi_video_controls_text),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 视频控制覆盖层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(id = R.color.kimi_video_overlay))
                        .padding(dimensionResource(id = R.dimen.kimi_video_overlay_padding)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onVideoToggle,
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = colorResource(id = R.color.kimi_video_controls_bg),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止视频",
                                tint = colorResource(id = R.color.kimi_video_controls_text),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // 视频关闭状态
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = "视频",
                    modifier = Modifier.size(dimensionResource(id = R.dimen.kimi_video_placeholder_icon_size)),
                    tint = colorResource(id = R.color.kimi_video_controls_text)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isVideoEnabled) "视频已关闭" else "点击开启视频",
                    color = colorResource(id = R.color.kimi_video_controls_text),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                IconButton(
                    onClick = onVideoToggle,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_video_controls_bg),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isVideoEnabled) Icons.Default.PlayArrow else Icons.Default.Videocam,
                        contentDescription = if (isVideoEnabled) "开启视频" else "开启视频",
                        tint = colorResource(id = R.color.kimi_video_controls_text),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KimiVideoControls(
    isVideoEnabled: Boolean,
    isVideoActive: Boolean,
    onVideoToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.kimi_video_controls_height))
            .background(colorResource(id = R.color.kimi_video_controls_bg))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isVideoActive) "视频通话中" else "视频已关闭",
            color = colorResource(id = R.color.kimi_video_controls_text),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        IconButton(
            onClick = onVideoToggle,
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (isVideoActive) colorResource(id = R.color.kimi_button_danger) else colorResource(id = R.color.kimi_button_primary),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = if (isVideoActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isVideoActive) "停止视频" else "开启视频",
                tint = colorResource(id = R.color.kimi_text_primary),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
