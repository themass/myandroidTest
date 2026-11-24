package com.openapi.chatkit.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** 输入栏上方待发送的附件草稿。 */
@Immutable
data class DraftItem(
    val id: String,
    val type: DraftType,
    /** 图片为本地路径或地址；文件为本地路径；视频为缩略图路径（没有则空串）。 */
    val source: String,
    val name: String,
    /** 视频时长（秒），0 = 未知不显示；位置草稿上显示为坐标。 */
    val durationSeconds: Int = 0,
    /** 位置草稿的坐标文本（如「31.23, 121.47」）；其他类型为 null。 */
    val locationLabel: String? = null,
)

/** [Video] 显示缩略图（缺省退化成 MP4 徽标 + 时长）；[Location] 显示坐标 chip。 */
enum class DraftType { Image, File, Video, Location }

/** 「+」面板里的一个入口，由调用方决定有哪些入口、点了做什么。 */
@Immutable
data class ComposerAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 语音输入的三种样式。
 * - [Floating] 微信式：按住时在输入栏上方弹出浮层，显示随音量跳动的竖条。
 * - [Ripple] 豆包式：按住时向外扩散波纹，波纹随音量变大，并实时显示识别文字。
 * - [Bar] 录音条：按住的条变成录音条，显示计时和声波，左滑取消。
 */
enum class VoiceStyle { Floating, Ripple, Bar }

/** 输入栏需要的外部状态。输入栏内部的模式、面板展开等交互状态由组件自己管理。 */
@Immutable
data class ComposerState(
    val text: String = "",
    val drafts: List<DraftItem> = emptyList(),
    val sending: Boolean = false,
    /** 助手正在流式输出：右侧按钮变成「停止」。 */
    val generating: Boolean = false,
    /** 当前正在编辑一条用户消息：输入栏上方显示「编辑消息」提示条。 */
    val editing: Boolean = false,
    /** 转文字编辑态：识别出的文字（可编辑）。非空时输入栏上方显示编辑面板。 */
    val voiceDraftText: String? = null,
    val recording: Boolean = false,
    /** 录音结束、等待识别结果。 */
    val transcribing: Boolean = false,
    /** 录音中实时识别出的文字（样式 B 使用）。 */
    val liveTranscript: String = "",
    /** 当前录音音量 0..1，供声波动画使用。 */
    val voiceLevel: Float = 0f,
    /**
     * 生成中排队的纯文字条数（followup 队列）。>0 时输入栏上方显示「N 条排队中」；
     * 队列本身由调用方持有（feature-chat 的 ChatUiState.queued），chatkit 只管显示。
     */
    val queuedCount: Int = 0,
) {
    /**
     * 有内容就能按：生成中不再禁发送键（纯文字走排队续发，
     * 与 feature-chat 的 ChatUiState.canSend 同一语义）。
     */
    val canSend: Boolean get() = text.isNotBlank() || drafts.isNotEmpty()
}
