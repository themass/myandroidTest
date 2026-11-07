@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.ComposerState
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp

/**
 * 对话输入栏（参照微信 / 豆包）。
 *
 * - 左：语音 / 键盘切换。单击切换模式；长按循环切换语音样式，新样式通过 [onVoiceStyleChange] 交给调用方保存。
 * - 中：文字模式是输入框，语音模式是「按住说话」条（三种样式见 [VoiceStyle]）。
 * - 右：表情；有文字或有附件草稿时显示「发送」，否则是「+」（展开后旋转成 ×）。
 * - 上：附件草稿托盘、样式 B 的实时识别文字、样式 A 的录音浮层、识别中提示。
 * - 下：「+」面板（入口由 [actions] 决定）或表情面板。
 *
 * 外部状态由 [state] 提供；模式、面板展开、焦点等交互状态由组件自己管理。
 */
@Composable
fun ChatComposer(
    state: ComposerState,
    actions: List<ComposerAction>,
    voiceStyle: VoiceStyle,
    onVoiceStyleChange: (VoiceStyle) -> Unit,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onRemoveDraft: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    var voiceMode by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var canceling by remember { mutableStateOf(false) }
    val history = rememberLevelHistory(state.recording, state.voiceLevel)
    val showSend = !voiceMode && (state.text.isNotBlank() || state.drafts.isNotEmpty())
    val plusRotation by animateFloatAsState(if (showActions) 45f else 0f, label = "plus-rotation")
    val border by animateColorAsState(
        if (focused) colors.accent.copy(alpha = 0.5f) else colors.divider,
        label = "input-border",
    )

    Column(modifier.fillMaxWidth()) {
        if (state.recording && canceling) {
            // 上滑取消：底部泛起红色渐变，提示「松手取消」
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, colors.error.copy(alpha = 0.16f)),
                        ),
                    ),
            )
        }
        // 输入栏上方的提示区
        when {
            state.recording && voiceStyle == VoiceStyle.Ripple && state.liveTranscript.isNotBlank() ->
                Hint(text = state.liveTranscript, emphasized = false)
            state.recording && voiceStyle == VoiceStyle.Floating ->
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    VoiceFloatingPanel(canceling = canceling, history = history)
                }
            state.transcribing -> Hint(text = stringResource(R.string.chatkit_transcribing), emphasized = true)
        }

        Surface(color = colors.surface) {
            Column {
                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                if (state.drafts.isNotEmpty()) {
                    DraftTray(drafts = state.drafts, onRemove = onRemoveDraft)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BarIcon(
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                voiceMode = !voiceMode
                                showActions = false
                                showEmoji = false
                            },
                            onLongClick = {
                                val next = VoiceStyle.entries[(voiceStyle.ordinal + 1) % VoiceStyle.entries.size]
                                onVoiceStyleChange(next)
                            },
                        ),
                    ) {
                        Icon(
                            if (voiceMode) Icons.Filled.Keyboard else Icons.Outlined.Mic,
                            contentDescription = stringResource(if (voiceMode) R.string.chatkit_keyboard else R.string.chatkit_voice_toggle),
                            tint = colors.bubbleText,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        if (voiceMode) {
                            VoicePressBar(
                                style = voiceStyle,
                                recording = state.recording,
                                canceling = canceling,
                                level = state.voiceLevel,
                                history = history,
                                onCancelingChange = { canceling = it },
                                onStart = onVoiceStart,
                                onStop = onVoiceStop,
                                onCancel = {
                                    canceling = false
                                    onVoiceCancel()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            InputField(
                                text = state.text,
                                border = border,
                                onTextChange = onTextChange,
                                onFocusChange = { isFocused ->
                                    focused = isFocused
                                    if (isFocused) {
                                        showActions = false
                                        showEmoji = false
                                    }
                                },
                            )
                        }
                    }

                    if (!voiceMode) {
                        BarIcon(onClick = { showEmoji = !showEmoji; showActions = false }) {
                            Icon(
                                Icons.Outlined.EmojiEmotions,
                                contentDescription = stringResource(R.string.chatkit_emoji),
                                tint = if (showEmoji) colors.accent else colors.bubbleText,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    if (state.generating && !voiceMode) {
                        // 生成中：圆形「停止」按钮，点一下中断，已生成的内容保留
                        Surface(
                            onClick = onStop,
                            shape = CircleShape,
                            color = colors.accent,
                            modifier = Modifier.padding(start = 2.dp, end = 6.dp, bottom = 10.dp).size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(Modifier.size(12.dp).background(colors.onAccent, RoundedCornerShape(2.dp)))
                            }
                        }
                    } else if (showSend) {
                        Surface(
                            onClick = {
                                // 发送后收起表情和「+」面板；键盘保留，方便继续输入
                                showEmoji = false
                                showActions = false
                                onSend()
                            },
                            enabled = state.canSend,
                            shape = RoundedCornerShape(18.dp),
                            color = if (state.canSend) colors.accent else colors.divider,
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp).height(36.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.chatkit_send),
                                    color = colors.onAccent,
                                    fontSize = ChatKit.typography.secondary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        BarIcon(onClick = { showActions = !showActions; showEmoji = false }) {
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = stringResource(R.string.chatkit_more),
                                tint = if (showActions) colors.accent else colors.bubbleText,
                                modifier = Modifier.size(26.dp).rotate(plusRotation),
                            )
                        }
                    }
                }
                when {
                    showActions -> ActionPanel(actions = actions)
                    showEmoji -> EmojiPanel(onEmoji = { onTextChange(state.text + it) })
                }
            }
        }
    }
}

/** 输入栏两侧的图标按钮：统一 42dp 点击区域、底部对齐到输入框。 */
@Composable
private fun BarIcon(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        IconButton(onClick = onClick, modifier = modifier.size(width = 44.dp, height = 56.dp)) { content() }
    } else {
        Box(modifier.size(width = 44.dp, height = 56.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun InputField(
    text: String,
    border: androidx.compose.ui.graphics.Color,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val colors = ChatKit.colors
    val typography = ChatKit.typography
    Surface(
        shape = RoundedCornerShape(21.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = TextStyle(fontSize = 17.sp, color = colors.bubbleText),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 5,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.chatkit_input_hint), fontSize = 17.sp, color = colors.secondaryText)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun Hint(text: String, emphasized: Boolean) {
    val colors = ChatKit.colors
    Surface(color = if (emphasized) colors.accent.copy(alpha = 0.08f) else colors.surface) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = if (emphasized) TextAlign.Center else TextAlign.Start,
            fontSize = if (emphasized) ChatKit.typography.caption else ChatKit.typography.body,
            color = if (emphasized) colors.accent else colors.bubbleText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
