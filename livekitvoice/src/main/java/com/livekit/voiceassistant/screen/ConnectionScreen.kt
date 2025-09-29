package com.livekit.voiceassistant.screen

import android.widget.Toast
import com.github.ajalt.timberkt.Timber
import com.livekit.voiceassistant.error.GlobalErrorHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.retrieveToken
import com.livekit.voiceassistant.ui.theme.Blue500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable


@Serializable
object ConnectRoute

@Composable
fun ConnectionScreen(
    url: String,
    token: String,
    navigateToVoiceAssistant: (url: String, token: String) -> Unit,
    activity: androidx.activity.ComponentActivity? = null
) {

    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(R.drawable.connect_icon), contentDescription = "Connect icon")

            Spacer(Modifier.size(16.dp))
            Text(
                text = buildAnnotatedString {
                    append("Start a call to chat with your voice agent. Need help getting set up?\nCheck out the ")
                    withLink(
                        LinkAnnotation.Url(
                            "https://docs.livekit.io/agents/start/voice-ai/",
                            TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                        )
                    ) {
                        append("Voice AI quickstart.")
                    }
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            var hasError by rememberSaveable { mutableStateOf(false) }
            var isConnecting by remember { mutableStateOf(false) }

            Spacer(Modifier.size(8.dp))

            AnimatedVisibility(hasError) {
                Text(
                    text = "Error connecting. Make sure your agent is properly configured and try again.",
                    color = Color.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }

            Spacer(Modifier.size(24.dp))

            val buttonColors = ButtonDefaults.buttonColors(
                containerColor = Blue500,
                contentColor = Color.White
            )
            Button(
                colors = buttonColors,
                shape = RoundedCornerShape(20),
                onClick = {
                    isConnecting = true
                    hasError = false
                    coroutineScope.launch {
                        try {
                            delay(1000)
                            
                            // 使用传入的url和token参数，不使用硬编码值
                            val serverUrl = url
                            val participantToken = token
                            
                            println("🔗 ConnectionScreen: 使用传入的参数 - URL: $serverUrl, Token: ${token.take(20)}...")
                            
                            // 验证参数
                            if (!com.livekit.voiceassistant.SafeConnectionManager.isValidUrl(serverUrl)) {
                                hasError = true
                                if (activity != null) {
                                    Toast.makeText(activity, "无效的服务器 URL", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            
                            if (!com.livekit.voiceassistant.SafeConnectionManager.isValidToken(participantToken)) {
                                hasError = true
                                if (activity != null) {
                                    Toast.makeText(activity, "无效的 Token", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            
                            // 检查网络连接
                            if (activity != null && !com.livekit.voiceassistant.SafeConnectionManager.isNetworkAvailable(activity)) {
                                hasError = true
                                Toast.makeText(activity, "网络连接不可用", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            
                            // 导航到 VoiceAssistant 屏幕
                            navigateToVoiceAssistant(serverUrl, participantToken)
                            
                        } catch (exception: Exception) {
                            hasError = true
                            // 使用错误处理器显示具体错误信息
                            if (activity != null) {
                                GlobalErrorHandler.addError(
                                    GlobalErrorHandler.createConnectionError("连接失败: ${exception.message}")
                                )
                            }
                            // 记录详细错误信息
                            Timber.e(exception) { "LiveKit 连接失败: ${exception.message}" }
                        } finally {
                            isConnecting = false
                        }
                    }
                }
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(isConnecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                trackColor = Color.Gray,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                    Text(
                        text = if (isConnecting) "CONNECTING" else "START CALL",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        )
                    )
                }
            }
        }
    }
}