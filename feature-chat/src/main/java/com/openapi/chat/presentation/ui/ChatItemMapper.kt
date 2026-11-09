package com.openapi.chat.presentation.ui

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Reaction
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.core.media.AudioPlayback

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
        } else if (message.text.isNotEmpty()) {
            add(MessagePart.Text(message.text))
        }
    }
}
