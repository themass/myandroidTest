package com.openapi.chatkit.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** 输入栏上方待发送的附件草稿。 */
@Immutable
data class DraftItem(
    val id: String,
    val type: DraftType,
    /** 图片为本地路径或地址；文件为本地路径。 */
    val source: String,
    val name: String,
)

enum class DraftType { Image, File }

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
    val recording: Boolean = false,
    /** 录音结束、等待识别结果。 */
    val transcribing: Boolean = false,
    /** 录音中实时识别出的文字（样式 B 使用）。 */
    val liveTranscript: String = "",
    /** 当前录音音量 0..1，供声波动画使用。 */
    val voiceLevel: Float = 0f,
) {
    val canSend: Boolean get() = (text.isNotBlank() || drafts.isNotEmpty()) && !sending
}
