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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.ajalt.timberkt.Timber
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberVoiceAssistant
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import io.livekit.android.LiveKit
import io.livekit.android.compose.state.getTrackReferencesBySource
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.audio.VoiceAssistantBarVisualizer
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.util.LoggingLevel
import livekit.org.webrtc.AudioSource

@Beta
class LivekitVoiceAgentFragment : Fragment() {
    private var url: String? = null
    private var token: String? = null
    private lateinit var localParticipant: LocalParticipant

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG

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

            RoomScope(
                url,
                token,
                audio = true,
                connect = true
            ) { room ->

                val voiceAssistant = rememberVoiceAssistant()
                val agentState = voiceAssistant.state
                LaunchedEffect(key1 = agentState) {
                    Timber.i { "agent state: $agentState" }
                }
                
                // 简化的 UI，只显示基本的连接状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.8f))
                ) {
                    Text(
                        text = "LiveKit Voice Assistant - Old Style",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        fontSize = 18.sp
                    )
                }
                
                localParticipant = room.localParticipant

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
                            height = Dimension.matchParent
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
                            Text(
                                text = segment.text,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
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