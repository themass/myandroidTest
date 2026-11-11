package com.openapi.chat.presentation

import com.openapi.chat.ChatDependencies
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.model.VoiceAttachment
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.speech.audio.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音消息：录音 → 存成消息 → 转写 → 当作文本发给大模型；也支持「转文字」编辑态。
 * 从 ChatViewModel 拆出来。ASR 通过 [ChatDependencies.speechToText] 注入，未注入时只保留语音气泡。
 */
internal class VoiceActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val sendText: (String) -> Unit,
    private val streamReply: (String, String?, Boolean) -> Unit,
) {

    // ------------------------------------------------------------------
    // 语音消息：录音 → 存成消息 → 转写 → 当作文本发给大模型。
    // ASR 通过 [SpeechToText] 注入（实现是 app 里的字节引擎封装），未注入时
    // 只保留语音气泡不转写，和之前的降级行为一致。
    // ------------------------------------------------------------------

    private var liveAsr: com.openapi.speech.asr.VolcengineStreamingAsrClient? = null

    /** 开始录音；[liveTranscript] 为 true 时同时开启实时识别（语音样式 B）。 */
    fun start(context: android.content.Context, liveTranscript: Boolean) {
        if (state.value.recording) return
        val started = VoiceRecorder.start(context)
        state.update { it.copy(recording = started, partialText = "") }
        if (started && liveTranscript) startLiveTranscript(context)
    }

    /**
     * 样式 B：录音的同时把 PCM 送给火山流式识别，识别出的文字实时显示在输入栏上方。
     * 最终发送的文字仍然以松手后的后端识别结果为准。
     */
    private fun startLiveTranscript(context: android.content.Context) {
        val config = com.openapi.speech.asr.VolcengineAsrConfig.load(context)
        val client = com.openapi.speech.asr.VolcengineStreamingAsrClient(config)
        liveAsr = client
        client.connect(object : com.openapi.speech.asr.VolcengineStreamingAsrClient.Listener {
            override fun onReady() = Unit
            override fun onPartialText(text: String) {
                state.update { it.copy(partialText = text) }
            }
            override fun onFailure(message: String) {
                liveAsr = null
            }
            override fun onClosed() = Unit
        })
        VoiceRecorder.pcmListener = { buffer, length ->
            // 录音线程会复用 buffer，发送前先拷贝一份
            client.sendPcm(buffer.copyOf(length), 0, length, false)
        }
    }

    private fun stopLiveTranscript() {
        VoiceRecorder.pcmListener = null
        liveAsr?.finish()
        liveAsr = null
    }

    /** 上滑取消手势触发；丢弃本次录音，不生成消息。 */
    fun cancel() {
        stopLiveTranscript()
        VoiceRecorder.cancel()
        state.update { it.copy(recording = false, partialText = "") }
    }

    /**
     * 「转文字」：松手后不直接发送。录音文件保留，识别出的文字进入编辑态（[ChatUiState.voiceDraft]），
     * 用户可以改文字后发送文字、发送原语音，或取消（取消时删除录音文件）。
     */
    fun stopToText() {
        if (!state.value.recording) return
        stopLiveTranscript()
        state.update { it.copy(recording = false, partialText = "", transcribing = true) }
        scope.launch {
            val result = withContext(Dispatchers.IO) { VoiceRecorder.stop() }
            val speech = ChatDependencies.speechToText()
            val text = if (result == null || speech == null) {
                null
            } else {
                runCatching { speech.transcribe(result.filePath) }.getOrNull()
            }
            state.update { current ->
                current.copy(
                    transcribing = false,
                    voiceDraft = result?.let { VoiceDraft(it.filePath, it.durationSeconds, text.orEmpty()) },
                )
            }
        }
    }

    /** 编辑态：发送改好的文字（作为一条普通文字消息，并请求回复）。录音文件删除。 */
    fun draftSendText(text: String) {
        val draft = state.value.voiceDraft ?: return
        discardDraftFile(draft)
        state.update { it.copy(voiceDraft = null) }
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) sendText(trimmed)
    }

    /** 编辑态：只发送原语音（语音气泡，文字为识别结果，不请求回复）。 */
    fun draftSendAudio() {
        val draft = state.value.voiceDraft ?: return
        state.update { it.copy(voiceDraft = null) }
        scope.launch {
            val message = ChatMessage(
                id = "local-voice-${System.currentTimeMillis()}",
                role = ChatRole.USER,
                text = draft.text,
                createdAtMillis = System.currentTimeMillis(),
                kind = MessageKind.VOICE,
                voice = VoiceAttachment(uri = "file://${draft.filePath}", durationSeconds = draft.durationSeconds),
            )
            repository.saveMessage(repository.currentSessionId(), message)
            state.update { it.copy(messages = it.messages + message) }
        }
    }

    /** 编辑态：取消，删除录音文件，回到普通输入。 */
    fun draftCancel() {
        val draft = state.value.voiceDraft ?: return
        discardDraftFile(draft)
        state.update { it.copy(voiceDraft = null) }
    }

    private fun discardDraftFile(draft: VoiceDraft) {
        runCatching { java.io.File(draft.filePath).delete() }
    }

    fun stopAndSend() {
        if (!state.value.recording) return
        stopLiveTranscript()
        state.update { it.copy(recording = false, partialText = "") }
        scope.launch {
            val result = withContext(Dispatchers.IO) { VoiceRecorder.stop() } ?: return@launch
            val message = ChatMessage(
                id = "local-voice-${System.currentTimeMillis()}",
                role = ChatRole.USER,
                text = "",
                createdAtMillis = System.currentTimeMillis(),
                kind = MessageKind.VOICE,
                voice = VoiceAttachment(uri = "file://${result.filePath}", durationSeconds = result.durationSeconds),
            )
            repository.saveMessage(repository.currentSessionId(), message)
            state.update { it.copy(messages = it.messages + message) }

            val speech = ChatDependencies.speechToText() ?: return@launch
            state.update { it.copy(transcribing = true) }
            val text = runCatching { speech.transcribe(result.filePath) }.getOrNull()
            state.update { it.copy(transcribing = false) }

            // 转写失败就停在这里：语音气泡已经发出去了，不拿空串去问大模型。
            if (text.isNullOrBlank()) return@launch
            // 转写显示在语音气泡下方（同一条消息），并写回本地记录，重启后也能看到
            val transcribed = message.copy(text = text)
            repository.saveMessage(repository.currentSessionId(), transcribed)
            state.update { current ->
                current.copy(messages = current.messages.map { if (it.id == message.id) transcribed else it })
            }
            // 语音气泡已经保存过：这里不能再让仓库保存用户消息。仓库保存的是纯文字行，会用同一个 chatId
            // 覆盖掉语音记录，语音就没了（重试路径同样是 persistUser = false）。
            streamReply(text, message.id, false)
        }
    }
}
