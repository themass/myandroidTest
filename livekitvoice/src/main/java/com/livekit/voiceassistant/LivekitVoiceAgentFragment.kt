package com.livekit.voiceassistant

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.fragment.app.Fragment
import com.github.ajalt.timberkt.Timber
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberVoiceAssistant
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import com.livekit.voiceassistant.ui.UserTranscription
import com.livekit.voiceassistant.wave.MyWaveView
import io.livekit.android.compose.ui.audio.VoiceAssistantBarVisualizer

@Beta
class LivekitVoiceAgentFragment : Fragment() {
    private var url: String? = null
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            url = it.getString("url")
            token = it.getString("token")
            Log.i("myTag", "token：$token")
            Log.i("myTag", "url:$url")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ComposeView(requireContext())
        view.setContent {
            url?.let { url ->
                token?.let { token ->
                    VoiceAssistantFragmentContent(url, token)
                }
            }
        }
        return view
    }

    @Composable
    fun VoiceAssistantFragmentContent(url: String, token: String) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (audioVisualizer, chatLog) = createRefs()

            // 背景虚化效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(10.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            ) {
                AndroidView(factory = { context ->
                    MyWaveView(context)
                })
            }

            RoomScope(
                url,
                token,
                audio = true,
                connect = true
            ) { room ->
                Timber.i { "Token: $token" }

                val voiceAssistant = rememberVoiceAssistant()
                val agentState = voiceAssistant.state
                LaunchedEffect(key1 = agentState) {
                    Timber.i { "agent state: $agentState" }
                }

                // 获取转录内容
                val segments = rememberTranscriptions()
                val localSegments = rememberParticipantTranscriptions(room.localParticipant)
                val lazyListState = rememberLazyListState()

                LazyColumn(
                    userScrollEnabled = true,
                    state = lazyListState,
                    modifier = Modifier
                        .constrainAs(chatLog) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            height = Dimension.percent(0.9f)
                            width = Dimension.fillToConstraints
                        }
                ) {
                    items(
                        items = segments,
                        key = { segment -> segment.id },
                    ) { segment ->
                        val isLocal = localSegments.contains(segment)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            // 消息气泡
                            Surface(
                                modifier = Modifier
                                    .align(if (isLocal) Alignment.CenterEnd else Alignment.CenterStart)
                                    .widthIn(max = 280.dp),
                                color = if (isLocal) Color.Blue.copy(alpha = 0.2f)
                                else Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    // 角标
                                    Text(
                                        text = if (isLocal) "YOU" else "AI",
                                        color = if (isLocal) Color.Blue else Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 4.dp, end = 8.dp)
                                    )

                                    // 消息内容
                                    Box(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        if (isLocal) {
                                            UserTranscription(
                                                segment = segment,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                text = segment.text,
                                                fontSize = 15.sp,
                                                color = Color.Black,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 自动滚动到底部
                LaunchedEffect(segments) {
                    if (segments.isNotEmpty()) {
                        lazyListState.animateScrollToItem(segments.size - 1)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(url: String, token: String): LivekitVoiceAgentFragment {
            val fragment = LivekitVoiceAgentFragment()
            val args = Bundle()
            args.putString("url", url)
            args.putString("token", token)
            fragment.arguments = args
            return fragment
        }
    }
}
