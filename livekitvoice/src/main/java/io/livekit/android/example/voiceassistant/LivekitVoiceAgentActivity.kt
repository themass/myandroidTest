@file:OptIn(Beta::class)

package io.livekit.android.example.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.github.ajalt.timberkt.Timber
import io.livekit.android.LiveKit
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberVoiceAssistant
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import io.livekit.android.compose.ui.audio.VoiceAssistantBarVisualizer
import io.livekit.android.example.voiceassistant.ui.UserTranscription
import io.livekit.android.example.voiceassistant.ui.theme.LiveKitVoiceAssistantExampleTheme
import io.livekit.android.util.LoggingLevel

class LivekitVoiceAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG
        requireNeededPermissions {
            requireToken { url, token ->
                Timber.i { "Token: $token" }
                setContent {
                    LiveKitVoiceAssistantExampleTheme {
                        VoiceAssistant(
                            url,
                            token,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun VoiceAssistant(url: String, token: String, modifier: Modifier = Modifier) {
        ConstraintLayout(modifier = modifier) {
            RoomScope(
                url,
                token,
                audio = true,
                connect = true,
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
}