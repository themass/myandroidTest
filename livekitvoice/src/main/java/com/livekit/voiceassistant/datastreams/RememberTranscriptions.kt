package com.livekit.voiceassistant.datastreams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.TextStreamInfo
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.types.TranscriptionSegment
import io.livekit.android.room.types.merge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

private const val TRANSCRIPTION_TOPIC = "lk.transcription"
private const val CHAT_TOPIC = "lk.chat" // 聊天消息话题

data class Transcription(
    val identity: Participant.Identity,
    val transcriptionSegment: TranscriptionSegment,
)

data class TranscriptionsState(
    val transcriptions: State<List<Transcription>>,
    /**
     * For manually adding local transcriptions to the state.
     */
    val addTranscription: (identity: Participant.Identity, message: String) -> Unit
)

/**
 * Listens for incoming transcription data streams, and returns
 * all received transcriptions ordered by first received time.
 */
@Composable
fun rememberTranscriptions(room: Room): TranscriptionsState {
    val coroutineScope = rememberCoroutineScope()
    val transcriptions = remember(room) { mutableStateMapOf<String, Transcription>() }
    val orderedTranscriptions = remember(transcriptions) {
        derivedStateOf {
            transcriptions.values.sortedBy { segment -> segment.transcriptionSegment.firstReceivedTime }
        }
    }
    val transcriptionsState = remember(transcriptions, orderedTranscriptions) {
        TranscriptionsState(
            orderedTranscriptions
        ) { identity, message ->
            transcriptions.mergeNewSegments(
                listOf(
                    Transcription(
                        identity = identity,
                        TranscriptionSegment(
                            id = UUID.randomUUID().toString(),
                            text = message,
                            language = "",
                            final = true,
                            firstReceivedTime = Date().time,
                            lastReceivedTime = Date().time,
                        )
                    )
                )
            )
        }
    }

    DisposableEffect(room) {
        
        // 注册转录消息处理器
        room.registerTextStreamHandler(TRANSCRIPTION_TOPIC) { receiver, identity ->
            coroutineScope.launch(Dispatchers.IO) {
                // Prepare for incoming transcription
                val segment = createTranscriptionSegment(streamInfo = receiver.info)
                val stringBuilder = StringBuilder()

                // Collect the incoming transcription stream.
                receiver.flow.collect { transcription ->
                    stringBuilder.append(transcription)

                    transcriptions.mergeNewSegments(
                        listOf(
                            Transcription(
                                identity = identity,
                                segment.copy(
                                    text = stringBuilder.toString(),
                                    lastReceivedTime = Date().time
                                )
                            )
                        )
                    )
                }
            }
        }
        
        // 注册聊天消息处理器
        room.registerTextStreamHandler(CHAT_TOPIC) { receiver, identity ->
            coroutineScope.launch(Dispatchers.IO) {
                val stringBuilder = StringBuilder()

                // 收集聊天消息流
                receiver.flow.collect { chatMessage ->
                    stringBuilder.append(chatMessage)
                    
                    // 创建聊天消息的转录段
                    val chatSegment = TranscriptionSegment(
                        id = UUID.randomUUID().toString(),
                        text = stringBuilder.toString(),
                        language = "zh-CN", // 默认中文
                        final = true, // 聊天消息都是完整的
                        firstReceivedTime = Date().time,
                        lastReceivedTime = Date().time
                    )
                    
                    transcriptions.mergeNewSegments(
                        listOf(
                            Transcription(
                                identity = identity,
                                transcriptionSegment = chatSegment
                            )
                        )
                    )
                    
                    println("收到聊天消息: $chatMessage from $identity")
                }
            }
        }

        onDispose {
            // 清理处理器
            room.unregisterTextStreamHandler(TRANSCRIPTION_TOPIC)
            room.unregisterTextStreamHandler(CHAT_TOPIC)
        }
    }

    return transcriptionsState
}

private fun createTranscriptionSegment(streamInfo: TextStreamInfo): TranscriptionSegment {
    return TranscriptionSegment(
        id = streamInfo.attributes["lk.segment_id"] ?: "",
        text = "",
        language = "",
        final = streamInfo.attributes["lk.transcription.final"]?.toBoolean() ?: false,
        firstReceivedTime = Date().time,
        lastReceivedTime = Date().time
    )
}

/**
 * Merges new transcriptions into an existing map.
 */
fun MutableMap<String, Transcription>.mergeNewSegments(newTranscriptions: Collection<Transcription>) {
    for (transcription in newTranscriptions) {
        val existingTranscription = get(transcription.transcriptionSegment.id)
        put(
            transcription.transcriptionSegment.id,
            Transcription(
                identity = transcription.identity,
                transcriptionSegment = existingTranscription?.transcriptionSegment.merge(transcription.transcriptionSegment)
            )
        )
    }
}