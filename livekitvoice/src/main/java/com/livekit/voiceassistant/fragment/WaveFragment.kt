package com.livekit.voiceassistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.CustomWaveView
import com.livekit.voiceassistant.component.CustomWaveType
import com.livekit.voiceassistant.datastreams.rememberTranscriptions
import com.livekit.voiceassistant.datastreams.Transcription
import com.livekit.voiceassistant.ui.ChatLogPreview
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.types.TranscriptionSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原始波纹效果Fragment - 使用AXWaveAnimation库
 * 恢复原始的UI效果，保持与之前版本的一致性
 * 
 * 布局：
 * - 中间：圆形波纹代表Agent
 * - 底部：线性波纹代表User
 * - 消息显示区域：使用ChatLog组件
 * - 连接按钮：点击连接服务器
 */
class WaveFragment : Fragment() {
    private var url: String? = null
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let {
            url = it.getString("url") ?: it.getString("URL")
            token = it.getString("token") ?: it.getString("TOKEN")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                if (url != null && token != null) {
                    OriginalWaveContent(url!!, token!!)
                } else {
                    ErrorContent("缺少 URL 和 TOKEN 参数")
                }
            }
        }
    }

    @Composable
    private fun OriginalWaveContent(url: String, token: String) {
        // Kimi主题
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colorResource(id = R.color.kimi_primary),
                secondary = colorResource(id = R.color.kimi_secondary),
                background = colorResource(id = R.color.kimi_old_bg_middle)
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
                // 使用RoomScope来管理LiveKit连接
                RoomScope(
                    url = url,
                    token = token,
                    audio = true,
                    video = false,
                    connect = false // 不自动连接，由用户手动连接
                ) { room ->
                    val transcriptionsState = rememberTranscriptions(room)
                    
                    // 原始波纹效果展示
                    OriginalWaveDemo(room, transcriptionsState)
                }
            }
        }
    }

    @Composable
    private fun OriginalWaveDemo(
        room: io.livekit.android.room.Room,
        transcriptionsState: com.livekit.voiceassistant.datastreams.TranscriptionsState
    ) {
        // 状态管理
        var isConnected by remember { mutableStateOf(false) }
        var agentAmplitude by remember { mutableStateOf(0f) }
        var userAmplitude by remember { mutableStateOf(0f) }
        var isAgentActive by remember { mutableStateOf(false) }
        var isUserActive by remember { mutableStateOf(false) }
        
        // 模拟音频活动
        LaunchedEffect(isConnected) {
            while (isConnected) {
                kotlinx.coroutines.delay(3000)
                
                // 模拟Agent说话
                isAgentActive = true
                agentAmplitude = 0.8f
                kotlinx.coroutines.delay(2000)
                isAgentActive = false
                agentAmplitude = 0f
                
                kotlinx.coroutines.delay(1000)
                
                // 模拟用户说话
                isUserActive = true
                userAmplitude = 0.6f
                kotlinx.coroutines.delay(1500)
                isUserActive = false
                userAmplitude = 0f
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部标题和状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "原始波纹效果演示",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    color = if (isConnected) colorResource(id = R.color.kimi_green_success) else colorResource(id = R.color.kimi_red_danger),
                    fontSize = 14.sp
                )
            }
            
            // 中间Agent圆形波纹
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Agent圆形波纹
                CustomWaveView(
                    isActive = isAgentActive,
                    amplitude = agentAmplitude,
                    waveType = CustomWaveType.CIRCULAR,
                    modifier = Modifier.size(200.dp)
                )
                
                // Agent标识
                Text(
                    text = "Agent",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            
            // 消息显示区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                // 使用ChatLog组件显示消息
                ChatLogPreview(
                    transcriptions = if (isConnected) {
                        listOf(
                            Transcription(
                                identity = Participant.Identity("agent"),
                                transcriptionSegment = TranscriptionSegment(
                                    id = "1",
                                    text = "你好！我是AI助手，有什么可以帮助您的吗？",
                                    lastReceivedTime = System.currentTimeMillis() - 5000,
                                    language = "zh-CN",
                                    final = true
                                )
                            ),
                            Transcription(
                                identity = Participant.Identity("user"),
                                transcriptionSegment = TranscriptionSegment(
                                    id = "2",
                                    text = "请介绍一下这个产品的功能",
                                    lastReceivedTime = System.currentTimeMillis() - 3000,
                                    language = "zh-CN",
                                    final = true
                                )
                            )
                        )
                    } else emptyList(),
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 底部用户线性波纹和连接按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                // 用户线性波纹
                CustomWaveView(
                    isActive = isUserActive,
                    amplitude = userAmplitude,
                    waveType = CustomWaveType.LINEAR,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.TopCenter)
                )
                
                // 用户标识
                Text(
                    text = "User",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                
                // 连接按钮
                Button(
                    onClick = { 
                        isConnected = !isConnected
                        // 简化连接逻辑，只改变状态
                        // 实际连接由RoomScope管理
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) 
                            colorResource(id = R.color.kimi_red_danger) 
                        else 
                            colorResource(id = R.color.kimi_green_success)
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(
                        text = if (isConnected) "断开连接" else "连接服务器",
                        color = colorResource(id = R.color.kimi_white_full)
                    )
                }
            }
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.kimi_old_bg_middle)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = colorResource(id = R.color.kimi_text_primary),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        fun newInstance(url: String, token: String): WaveFragment {
            val fragment = WaveFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "原始波纹效果演示")
@Composable
fun WaveFragmentPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(id = R.color.kimi_primary),
            secondary = colorResource(id = R.color.kimi_secondary),
            background = colorResource(id = R.color.kimi_old_bg_middle)
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
            OriginalWaveDemoPreview()
        }
    }
}

@Composable
private fun OriginalWaveDemoPreview() {
    // 状态管理
    var isConnected by remember { mutableStateOf(true) }
    var agentAmplitude by remember { mutableStateOf(0.8f) }
    var userAmplitude by remember { mutableStateOf(0.6f) }
    var isAgentActive by remember { mutableStateOf(true) }
    var isUserActive by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部标题和状态
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "原始波纹效果演示",
                color = colorResource(id = R.color.kimi_text_primary),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (isConnected) "已连接" else "未连接",
                color = if (isConnected) colorResource(id = R.color.kimi_green_success) else colorResource(id = R.color.kimi_red_danger),
                fontSize = 14.sp
            )
        }
        
        // 中间Agent圆形波纹
        Box(
            modifier = Modifier
                .size(250.dp)
                .background(
                    color = colorResource(id = R.color.kimi_white_alpha_10),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Agent圆形波纹
            CustomWaveView(
                isActive = isAgentActive,
                amplitude = agentAmplitude,
                waveType = CustomWaveType.CIRCULAR,
                modifier = Modifier.size(200.dp)
            )
            
            // Agent标识
            Text(
                text = "Agent",
                color = colorResource(id = R.color.kimi_text_primary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // 消息显示区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    color = colorResource(id = R.color.kimi_white_alpha_10),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // 使用ChatLog组件显示消息
            ChatLogPreview(
                transcriptions = listOf(
                    Transcription(
                        identity = Participant.Identity("agent"),
                        transcriptionSegment = TranscriptionSegment(
                            id = "1",
                            text = "你好！我是AI助手，有什么可以帮助您的吗？",
                            lastReceivedTime = System.currentTimeMillis() - 5000,
                            language = "zh-CN",
                            final = true
                        )
                    ),
                    Transcription(
                        identity = Participant.Identity("user"),
                        transcriptionSegment = TranscriptionSegment(
                            id = "2",
                            text = "请介绍一下这个产品的功能",
                            lastReceivedTime = System.currentTimeMillis() - 3000,
                            language = "zh-CN",
                            final = true
                        )
                    )
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 底部用户线性波纹和连接按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    color = colorResource(id = R.color.kimi_white_alpha_10),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // 用户线性波纹
            CustomWaveView(
                isActive = isUserActive,
                amplitude = userAmplitude,
                waveType = CustomWaveType.LINEAR,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
            )
            
            // 用户标识
            Text(
                text = "User",
                color = colorResource(id = R.color.kimi_text_primary),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            
            // 连接按钮
            Button(
                onClick = { /* 预览中不执行 */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) 
                        colorResource(id = R.color.kimi_red_danger) 
                    else 
                        colorResource(id = R.color.kimi_green_success)
                ),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(
                    text = if (isConnected) "断开连接" else "连接服务器",
                    color = colorResource(id = R.color.kimi_white_full)
                )
            }
        }
    }
}