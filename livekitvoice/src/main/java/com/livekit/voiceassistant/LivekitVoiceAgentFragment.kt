package com.livekit.voiceassistant

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
            RoomScope(
                url,
                token,
                audio = true,
                connect = true
            ) { room ->
                val (audioVisualizer, chatLog) = createRefs()
                Timber.i { "Token: $token" }

                val voiceAssistant = rememberVoiceAssistant()

                val agentState = voiceAssistant.state
                // Optionally do something with the agent state.
                LaunchedEffect(key1 = agentState) {
                    Timber.i { "agent state: $agentState" }
                }

                // Amplitude visualization of the Assistant's voice track.
                VoiceAssistantBarVisualizer(
                    voiceAssistant = voiceAssistant,
                    modifier = Modifier
                       .padding(8.dp)
                       .fillMaxWidth()
                       .constrainAs(audioVisualizer) {
                            height = Dimension.percent(0.1f)
                            width = Dimension.percent(0.8f)

                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                )
                AndroidView(factory = { context ->
                    MyWaveView(
                        context,
                        voiceAssistant
                    )
                })
                // Get and display the transcriptions.
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
                        Box(
                            modifier = Modifier
                               .fillMaxWidth()
                               .padding(8.dp)
                        ) {
                            if (localSegments.contains(segment)) {
                                UserTranscription(
                                    segment = segment,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            } else {
                                Text(
                                    text = segment.text,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }
                        }
                    }
                }

                // Scroll to bottom as new transcriptions come in.
                LaunchedEffect(segments) {
                    lazyListState.scrollToItem((segments.size - 1).coerceAtLeast(0))
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
