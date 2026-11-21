package com.openapi.feature.chat.presentation.ui

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Reaction
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.speech.audio.AudioPlayback

/**
 * 领域模型 → 组件库 UI 模型。组件库不认识 [ChatMessage]，所有显示相关的派生状态
 * （播放中、朗读中、赞踩、发送状态）都在这里合入。
 */
internal object ChatItemMapper {

    fun map(
        messages: List<ChatMessage>,
        playback: AudioPlayback,
        speakingId: String?,
        likedIds: Set<String>,
        dislikedIds: Set<String>,
    ): List<ChatItem> = messages.map { message ->
        ChatItem(
            id = message.id,
            key = message.key,
            sender = if (message.role == ChatRole.USER) Sender.User else Sender.Assistant,
            parts = parts(message, playback),
            status = when (message.delivery) {
                DeliveryStatus.SENT -> SendStatus.Sent
                DeliveryStatus.SENDING -> SendStatus.Sending
                DeliveryStatus.FAILED -> SendStatus.Failed
            },
            thinking = message.pending && message.text.isEmpty(),
            streaming = message.pending && message.text.isNotEmpty(),
            reaction = when (message.id) {
                in likedIds -> Reaction.Like
                in dislikedIds -> Reaction.Dislike
                else -> Reaction.None
            },
            speaking = message.id == speakingId,
        )
    }

    private fun parts(message: ChatMessage, playback: AudioPlayback): List<MessagePart> = buildList {
        if (message.images.isNotEmpty()) add(MessagePart.Images(message.images.map { it.path }))
        message.files.forEach { add(MessagePart.File(it.path, it.name, it.sizeBytes)) }
        message.videos.forEach {
            add(
                MessagePart.Video(
                    name = it.name,
                    durationSeconds = it.durationSeconds,
                    thumbnailPath = it.thumbnailPath.orEmpty(),
                    path = it.path,
                    uploadId = it.uploadId,
                ),
            )
        }
        message.location?.let {
            add(MessagePart.Location(it.latitude, it.longitude, it.accuracyMeters))
        }
        val voice = message.voice
        if (voice != null) {
            add(
                MessagePart.Voice(
                    durationSeconds = voice.durationSeconds,
                    // 语音消息的文字就是转写，显示在同一个语音气泡里
                    transcript = message.text.takeIf { it.isNotBlank() },
                    playing = playback.playing && playback.mediaId == message.id,
                    transcribing = message.pending,
                ),
            )
        } else if (showText(message)) {
            add(MessagePart.Text(message.text))
        }
    }

    /**
     * 位置消息的配文要不要单独画一个文字气泡。
     *
     * 服务端在发送方没写文字时会**自动生成**一行 `[位置] 31.23,121.47 (±120m)` 存进 content
     * （给模型看的），历史拉回来就是消息 text。位置气泡本身已经渲染坐标，
     * 再挂一条这种机器文字就是同一件事说两遍——所以本地自己发的（有配文才存）照常显示，
     * 服务端兜底生成的那行不显示。判定规则简单可靠：坐标文本与消息文字里包含
     * 「位置」标记，且本条消息**带坐标**时跳过。
     */
    private fun showText(message: ChatMessage): Boolean {
        if (message.text.isEmpty()) return false
        if (message.location == null) return true
        // 用户写过的配文原样回存（text == caption），只有服务端兜底才会命中这个自生成格式
        return !(message.text.startsWith("[位置]") || message.text.startsWith("[location]", ignoreCase = true))
    }
}
