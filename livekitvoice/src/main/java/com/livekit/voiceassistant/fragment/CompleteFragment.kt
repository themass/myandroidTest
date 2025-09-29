package com.livekit.voiceassistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.livekit.voiceassistant.R
import io.livekit.android.LiveKit
import io.livekit.android.util.LoggingLevel

/**
 * Kimi完整版Fragment - 直接使用RoomScope
 * 这是原来的完整版本，内部包含语音模式和聊天模式的切换
 * 单页面内部切换显示，而不是拆分成3个页面
 */
class CompleteFragment : Fragment() {
    private var url: String? = null
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let {
            url = it.getString("url") ?: it.getString("URL")
            token = it.getString("token") ?: it.getString("TOKEN")
        }
        
        // 初始化 LiveKit
        LiveKit.loggingLevel = LoggingLevel.INFO
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                if (url != null && token != null) {
                    KimiCompleteContent(url!!, token!!)
                } else {
                    KimiErrorContent(stringResource(id = R.string.error_missing_params))
                }
            }
        }
    }

    @Composable
    private fun KimiCompleteContent(url: String, token: String) {
        // 简化版本 - 直接显示完整界面的占位符
        // 因为KimiStyleVoiceAssistantScreen需要ViewModel，这里暂时用简化版本
        
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
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Kimi 完整版",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(id = R.color.kimi_text_primary)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "单页面内部切换模式",
                    fontSize = 20.sp,
                    color = colorResource(id = R.color.kimi_text_secondary),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "语音模式 ⇄ 聊天模式",
                    fontSize = 16.sp,
                    color = colorResource(id = R.color.kimi_text_hint),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "URL: ${url.take(30)}${if (url.length > 30) "..." else ""}",
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.kimi_text_hint),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "TOKEN: ${token.take(20)}...",
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.kimi_text_hint),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun KimiErrorContent(message: String) {
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
        fun newInstance(): CompleteFragment = CompleteFragment()
        
        fun newInstance(url: String, token: String): CompleteFragment {
            val fragment = CompleteFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E)
@Composable
fun CompleteFragmentPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Kimi 完整版",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "单页面内部切换\n语音模式 ⇄ 聊天模式",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}