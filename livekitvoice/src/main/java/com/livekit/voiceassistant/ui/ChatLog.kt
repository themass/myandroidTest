package com.livekit.voiceassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.datastreams.Transcription
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.types.TranscriptionSegment

@Composable
fun ChatLog(room: Room, transcriptions: List<Transcription>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Get and display the transcriptions.
        val displayTranscriptions = transcriptions.asReversed()
        val lazyListState = rememberLazyListState()

        // Scroll to bottom when new transcriptions come in.
        LaunchedEffect(transcriptions.count()) {
            lazyListState.animateScrollToItem(0)
        }
        LazyColumn(
            userScrollEnabled = true,
            state = lazyListState,
            reverseLayout = true,
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    // Fade top
                    val colors = arrayOf(
                        0.0f to Color.Transparent,
                        0.15f to Color.Black,
                        1.0f to Color.Black,
                    )
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = colors
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            // 如果没有消息，显示空状态
            if (displayTranscriptions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "开始对话...",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            items(
                items = displayTranscriptions,
                key = { transcription -> transcription.transcriptionSegment.id },
            ) { transcription ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp) // 消息气泡距离边框的距离减少到4dp
                        .animateItem()
                ) {
                    if (transcription.identity == room.localParticipant.identity) {
                        // 用户消息 - 右对齐，气泡宽度包裹文字，最左边距离左边框5dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(start = 5.dp) // 气泡最左边距离左边框5dp
                                .background(
                                    color = Color(0xFF4CAF50), // 绿色背景 - 匹配图片中的用户消息颜色
                                    shape = RoundedCornerShape(
                                        topStart = 15.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                                .padding(12.dp) // 恢复气泡内部间距
                        ) {
                            Text(
                                text = transcription.transcriptionSegment.text,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Agent消息 - 左对齐，气泡宽度包裹文字，最右边距离右边框15dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(end = 15.dp) // 气泡最右边距离右边框15dp
                                .background(
                                    color = Color(0xFFE0E0E0), // 灰白色背景 - 匹配图片中的Agent消息颜色
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 15.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                                .padding(12.dp) // 恢复气泡内部间距
                        ) {
                            Text(
                                text = transcription.transcriptionSegment.text,
                                color = Color.Black, // 灰白色背景配黑色文字
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// 预览用的简化版本
@Composable
fun ChatLogPreview(
    transcriptions: List<Transcription>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val displayTranscriptions = transcriptions.asReversed()
        val lazyListState = rememberLazyListState()

        LazyColumn(
            userScrollEnabled = true,
            state = lazyListState,
            reverseLayout = true,
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    // Fade top
                    val colors = arrayOf(
                        0.0f to Color.Transparent,
                        0.15f to Color.Black,
                        1.0f to Color.Black,
                    )
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = colors
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            items(
                items = displayTranscriptions,
                key = { transcription -> transcription.transcriptionSegment.id },
            ) { transcription ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    if (transcription.identity.toString() == "user") {
                        // 用户消息 - 右对齐
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(start = 5.dp)
                                .background(
                                    color = Color(0xFF4CAF50),
                                    shape = RoundedCornerShape(
                                        topStart = 15.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = transcription.transcriptionSegment.text,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Agent消息 - 左对齐，气泡宽度包裹文字，最右边距离右边框15dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(end = 15.dp) // 气泡最右边距离右边框15dp
                                .background(
                                    color = Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 15.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = transcription.transcriptionSegment.text,
                                color = Color.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "空消息列表")
@Composable
fun ChatLogEmptyPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
    ) {
        ChatLogPreview(
            transcriptions = emptyList(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "单条消息")
@Composable
fun ChatLogSingleMessagePreview() {
    val mockTranscriptions = listOf(
        Transcription(
            identity = Participant.Identity("user"),
            transcriptionSegment = TranscriptionSegment(
                id = "1",
                text = "你好，这是一条用户消息",
                lastReceivedTime = System.currentTimeMillis(),
                language = "zh-CN",
                final = true
            )
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
    ) {
        ChatLogPreview(
            transcriptions = mockTranscriptions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "对话消息")
@Composable
fun ChatLogConversationPreview() {
    val mockTranscriptions = listOf(
        Transcription(
            identity = Participant.Identity("user"),
            transcriptionSegment = TranscriptionSegment(
                id = "1",
                text = "你好，我想了解一下这个产品",
                lastReceivedTime = System.currentTimeMillis() - 10000,
                language = "zh-CN",
                final = true
            )
        ),
        Transcription(
            identity = Participant.Identity("agent"),
            transcriptionSegment = TranscriptionSegment(
                id = "2",
                text = "您好！很高兴为您介绍我们的产品。请问您对哪个方面比较感兴趣？",
                lastReceivedTime = System.currentTimeMillis() - 8000,
                language = "zh-CN",
                final = true
            )
        ),
        Transcription(
            identity = Participant.Identity("user"),
            transcriptionSegment = TranscriptionSegment(
                id = "3",
                text = "我想了解价格和功能特点",
                lastReceivedTime = System.currentTimeMillis() - 5000,
                language = "zh-CN",
                final = true
            )
        ),
        Transcription(
            identity = Participant.Identity("agent"),
            transcriptionSegment = TranscriptionSegment(
                id = "4",
                text = "好的，我们的产品具有以下特点：1. 高性能处理 2. 易于使用 3. 价格实惠。具体价格根据配置不同，基础版是99元，专业版是199元。",
                lastReceivedTime = System.currentTimeMillis() - 2000,
                language = "zh-CN",
                final = true
            )
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
    ) {
        ChatLogPreview(
            transcriptions = mockTranscriptions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "深色主题")
@Composable
fun ChatLogDarkThemePreview() {
    val mockTranscriptions = listOf(
        Transcription(
            identity = Participant.Identity("user"),
            transcriptionSegment = TranscriptionSegment(
                id = "1",
                text = "深色主题测试消息",
                lastReceivedTime = System.currentTimeMillis() - 5000,
                language = "zh-CN",
                final = true
            )
        ),
        Transcription(
            identity = Participant.Identity("agent"),
            transcriptionSegment = TranscriptionSegment(
                id = "2",
                text = "这是深色主题下的Agent回复消息，用来测试在深色背景下的显示效果。",
                lastReceivedTime = System.currentTimeMillis() - 2000,
                language = "zh-CN",
                final = true
            )
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp)
    ) {
        ChatLogPreview(
            transcriptions = mockTranscriptions,
            modifier = Modifier.fillMaxSize()
        )
    }
}