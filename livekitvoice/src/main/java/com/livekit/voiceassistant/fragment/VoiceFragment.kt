package com.livekit.voiceassistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.CustomWaveType
import com.livekit.voiceassistant.component.CustomWaveView
import com.livekit.voiceassistant.datastreams.Transcription
import com.livekit.voiceassistant.datastreams.TranscriptionsState
import com.livekit.voiceassistant.datastreams.rememberTranscriptions
import com.livekit.voiceassistant.ui.ChatLogPreview
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.types.TranscriptionSegment
import kotlinx.coroutines.delay

/**
 * 旧版语音助手Fragment - 使用原始自定义波纹效果
 * 这是之前版本的实现，保留作为对比和备用
 *
 * 布局：
 * - 中间：圆形波纹代表Agent
 * - 底部：线性波纹代表User
 * - 消息显示区域：使用ChatLog组件
 * - 连接按钮：点击连接服务器
 */
class VoiceFragment : Fragment() {
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
                    OldVoiceAgentContent(url!!, token!!)
                } else {
                    ErrorContent("缺少 URL 和 TOKEN 参数")
                }
            }
        }
    }

    @Composable
    private fun OldVoiceAgentContent(url: String, token: String) {
        // Kimi主题
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colorResource(id = R.color.kimi_primary),
                secondary = colorResource(id = R.color.kimi_secondary),
                background = colorResource(id = R.color.kimi_old_bg_middle)
            )
        ) {
            Box(
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .background(
                        brush = Brush.Companion.verticalGradient(
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

                    // 旧版波纹效果展示
                    OldVoiceAgentDemo(room, transcriptionsState)
                }
            }
        }
    }

    @Composable
    private fun OldVoiceAgentDemo(
        room: Room,
        transcriptionsState: TranscriptionsState
    ) {
        // 状态管理
        var isConnected by remember { mutableStateOf(false) }
        var agentAmplitude by remember { mutableStateOf(0f) }
        var userAmplitude by remember { mutableStateOf(0f) }
        var isAgentActive by remember { mutableStateOf(false) }
        var isUserActive by remember { mutableStateOf(false) }

        // 启用麦克风
        LaunchedEffect(room) {
            try {
                room.localParticipant.setMicrophoneEnabled(true)
                println("✅ 旧版Fragment: 麦克风已启用")
            } catch (e: Exception) {
                println("❌ 旧版Fragment: 麦克风启用失败: ${e.message}")
            }
        }

        // 监听真实的LiveKit连接状态
        LaunchedEffect(room) {
            if (room.localParticipant.identity != null) {
                isConnected = true
            } else {
                isConnected = false
            }
        }

        // 监听转录变化来检测Agent音频活动
        LaunchedEffect(transcriptionsState.transcriptions.value) {
            val transcriptions = transcriptionsState.transcriptions.value
            if (transcriptions.isNotEmpty()) {
                val lastTranscription = transcriptions.last()
                // 如果不是本地用户的消息，说明是Agent在说话
                if (lastTranscription.identity != room.localParticipant.identity) {
                    isAgentActive = true
                    agentAmplitude = 0.8f
                    println("🎵 旧版Fragment: 检测到Agent语音活动")
                    delay(1500)
                    isAgentActive = false
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
                    isUserActive = true
                    userAmplitude = 0.7f
                    println("🎵 旧版Fragment: 检测到远程参与者语音活动")
                } else {
                    isUserActive = false
                    userAmplitude = 0f
                }
            }
        }

        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Companion.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部标题和状态
            Column(
                horizontalAlignment = Alignment.Companion.CenterHorizontally
            ) {
                Text(
                    text = "旧版语音助手演示",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    color = if (isConnected) colorResource(id = R.color.kimi_green_success) else colorResource(
                        id = R.color.kimi_red_danger
                    ),
                    fontSize = 14.sp
                )
            }

            // 中间Agent圆形波纹
            Box(
                modifier = Modifier.Companion
                    .size(280.dp)
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Companion.Center
            ) {
                // Agent圆形波纹
                CustomWaveView(
                    isActive = isAgentActive,
                    amplitude = agentAmplitude,
                    waveType = CustomWaveType.CIRCULAR,
                    modifier = Modifier.Companion.size(220.dp)
                )

                // Agent标识
                Text(
                    text = "Agent",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion.align(Alignment.Companion.BottomCenter)
                )
            }

            // 消息显示区域
            Box(
                modifier = Modifier.Companion
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
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
                                    text = "你好！我是旧版AI助手，有什么可以帮助您的吗？",
                                    lastReceivedTime = System.currentTimeMillis() - 5000,
                                    language = "zh-CN",
                                    final = true
                                )
                            ),
                            Transcription(
                                identity = Participant.Identity("user"),
                                transcriptionSegment = TranscriptionSegment(
                                    id = "2",
                                    text = "请介绍一下这个旧版产品的功能",
                                    lastReceivedTime = System.currentTimeMillis() - 3000,
                                    language = "zh-CN",
                                    final = true
                                )
                            )
                        )
                    } else emptyList(),
                    modifier = Modifier.Companion.fillMaxSize()
                )
            }

            // 底部用户线性波纹和连接按钮
            Box(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        color = colorResource(id = R.color.kimi_white_alpha_10),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                // 用户线性波纹
                CustomWaveView(
                    isActive = isUserActive,
                    amplitude = userAmplitude,
                    waveType = CustomWaveType.LINEAR,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.Companion.TopCenter)
                )

                // 用户标识
                Text(
                    text = "User",
                    color = colorResource(id = R.color.kimi_text_primary),
                    fontSize = 16.sp,
                    modifier = Modifier.Companion.align(Alignment.Companion.CenterStart)
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
                    modifier = Modifier.Companion.align(Alignment.Companion.CenterEnd)
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
            modifier = Modifier.Companion
                .fillMaxSize()
                .background(colorResource(id = R.color.kimi_old_bg_middle)),
            contentAlignment = Alignment.Companion.Center
        ) {
            Text(
                text = message,
                color = colorResource(id = R.color.kimi_text_primary),
                fontSize = 16.sp,
                textAlign = TextAlign.Companion.Center
            )
        }
    }

    companion object {
        fun newInstance(url: String, token: String): VoiceFragment {
            val fragment = VoiceFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}