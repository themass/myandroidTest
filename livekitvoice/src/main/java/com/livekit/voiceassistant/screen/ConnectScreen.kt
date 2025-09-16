package com.livekit.voiceassistant.screen

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
fun ConnectScreen(
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
                        var connected = false
                        try {
                            delay(1000)
                            if (activity != null) {
                                val (serverUrl, participantToken) = activity.retrieveToken()
                                navigateToVoiceAssistant(serverUrl, participantToken)
                                connected = true
                            } else {
                                // Fallback to hardcoded values if no activity provided
                                navigateToVoiceAssistant("wss://testvoice-b8y35yvd.livekit.cloud", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUElTQU5ObUVHQlF4ZlkiLCJleHAiOjE3NTc3MTY2OTMsInN1YiI6InRoZW1hc3MiLCJqdGkiOiJ0aGVtYXNzIiwibmFtZSI6InRoZW1hc3MiLCJtZXRhZGF0YSI6Im1ldGFkYXRhIiwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJ0aGVtYXNzX3Jvb20ifSwic2lwIjp7fX0.8cuvwjvfoeh69h3X2XFiI0Q_IQoOs3JNedfI2FkNGPE")
                                connected = true
                            }
                        } catch (exception: Exception) {
                            hasError = true
                        } finally {
                            if (connected) {
                                // Add a delay so it updates after navigation
                                delay(1000)
                            }
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