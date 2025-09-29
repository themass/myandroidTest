package com.livekit.voiceassistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.screen.MainScreen
import com.livekit.voiceassistant.screen.ChatScreen
import com.livekit.voiceassistant.screen.VoiceScreen
import com.livekit.voiceassistant.screen.WaveScreen
import com.livekit.voiceassistant.screen.DemoScreen
import com.livekit.voiceassistant.screen.NewWaveScreen
import com.livekit.voiceassistant.datastreams.rememberTranscriptions
import io.livekit.android.compose.local.RoomScope
import kotlinx.serialization.Serializable

/**
 * 简化的 Kimi Fragment - 包含3个子页面
 * 1. 主页面 - 连接和状态
 * 2. 消息列表对话页 - 文字聊天
 * 3. 语音对话页 - 语音交互
 */
class SimpleFragment : Fragment() {
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
                    SimpleKimiContent(url!!, token!!)
                } else {
                    ErrorContent("缺少 URL 和 TOKEN 参数")
                }
            }
        }
    }

    @Composable
    private fun SimpleKimiContent(url: String, token: String) {
        val navController = rememberNavController()
        
        // Kimi 主题
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colorResource(id = R.color.kimi_primary),
                secondary = colorResource(id = R.color.kimi_secondary),
                surface = colorResource(id = R.color.kimi_surface),
                background = colorResource(id = R.color.kimi_background)
            )
        ) {
            // 深紫色渐变背景
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
                    connect = true
                ) { room ->
                    val transcriptionsState = rememberTranscriptions(room)
                    
                    // 导航管理 - 4个子页面
                    NavHost(navController, startDestination = MainRoute) {
                        composable<MainRoute> {
                            MainScreen(
                                url = url,
                                token = token,
                                onNavigateToChat = {
                                    navController.navigate(ChatRoute)
                                },
                                onNavigateToVoice = {
                                    navController.navigate(VoiceRoute)
                                },
                                onNavigateToWaveDemo = {
                                    navController.navigate(WaveDemoRoute)
                                },
                                onNavigateToOldWave = {
                                    navController.navigate(OldWaveRoute)
                                },
                                onNavigateToOriginalCustomWave = {
                                    navController.navigate(OriginalCustomWaveRoute)
                                }
                            )
                        }
                        
                        composable<ChatRoute> {
                            ChatScreen(
                                room = room,
                                transcriptionsState = transcriptionsState,
                                url = url,
                                token = token,
                                onBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    
                        composable<VoiceRoute> {
                            VoiceScreen(
                                url = url,
                                token = token,
                                onBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                        
                        composable<WaveDemoRoute> {
                            NewWaveScreen(
                                room = room,
                                transcriptionsState = transcriptionsState,
                                url = url,
                                token = token,
                                onBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                        
                        composable<OldWaveRoute> {
                            // 旧版波纹演示页面
                            DemoScreen(
                                url = url,
                                token = token,
                                onBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                        
                        composable<OriginalCustomWaveRoute> {
                            // 原始自定义波纹版本 - 恢复删除前的版本
                            WaveScreen(
                                url = url,
                                token = token,
                                onBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.kimi_background))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                color = colorResource(id = R.color.kimi_error),
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        fun newInstance(): SimpleFragment = SimpleFragment()
        
        fun newInstance(url: String, token: String): SimpleFragment {
            val fragment = SimpleFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}

// 导航路由
@Serializable
object MainRoute

@Serializable
object ChatRoute

@Serializable
object VoiceRoute

@Serializable
object WaveDemoRoute

@Serializable
object OldWaveRoute

@Serializable
object OriginalCustomWaveRoute

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E)
@Composable
fun SimpleFragmentPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF2C1810),
                        androidx.compose.ui.graphics.Color(0xFF1A0B2E),
                        androidx.compose.ui.graphics.Color(0xFF0F0511)
                    )
                )
            )
    ) {
        Text(
            text = "SimpleFragment\n3个子页面导航",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
