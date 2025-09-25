package com.livekit.voiceassistant_old

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.livekit.voiceassistant.R

/**
 * 旧版主Fragment - 语音助手主界面
 * 提供连接和导航功能
 */
class MainFragment : Fragment() {
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
                    OldMainContent(url!!, token!!)
                } else {
                    ErrorContent("缺少 URL 和 TOKEN 参数")
                }
            }
        }
    }

    @Composable
    private fun OldMainContent(url: String, token: String) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "旧版语音助手",
                        color = colorResource(id = R.color.kimi_text_primary),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "Kimi Voice Assistant (Old Version)",
                        color = colorResource(id = R.color.kimi_text_secondary),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )
                    
                    // 连接状态
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(id = R.color.kimi_white_alpha_10)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "连接信息",
                                color = colorResource(id = R.color.kimi_text_primary),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Text(
                                text = "服务器: ${url.take(50)}...",
                                color = colorResource(id = R.color.kimi_text_secondary),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                text = "Token: ${token.take(20)}...",
                                color = colorResource(id = R.color.kimi_text_secondary),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 启动按钮
                    Button(
                        onClick = {
                            // 启动语音助手Fragment
                            val fragment = LivekitVoiceAgentFragment.newInstance(url, token)
                            parentFragmentManager.beginTransaction()
                                .replace(android.R.id.content, fragment)
                                .addToBackStack("main")
                                .commit()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.kimi_primary)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "启动语音助手",
                            color = colorResource(id = R.color.kimi_white_full),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
        fun newInstance(url: String, token: String): MainFragment {
            val fragment = MainFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}
