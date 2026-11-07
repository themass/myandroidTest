@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.AddCircleOutline
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.ComposerState
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit

/**
 * 对话输入栏（参照微信 / 豆包）。
 *
 * - 中：输入胶囊。文字模式是输入框；语音模式胶囊变成「按住说话」条（样式见 [VoiceStyle]）。
 *   胶囊右端是语音 / 键盘切换按钮：单击切换模式，长按循环切换语音样式（通过 [onVoiceStyleChange] 交给调用方保存）。
 * - 右（胶囊外）：有文字或附件草稿时是「发送」，生成中是「停止」，否则是「+」（展开后旋转成 ×）。
 *   「+」会收起键盘并展开 [actions] 面板。
 * - 上：附件草稿托盘、识别中提示；录音时叠加 [VoiceOverlay]（不占布局高度）。
 *
 * 松手去向：发送 → [onVoiceStop]，取消 → [onVoiceCancel]，转文字（仅微信式右滑）→ [onVoiceToText]。
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
    onVoiceToText: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var voiceMode by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf(VoiceIntent.Send) }
    val history = rememberLevelHistory(state.recording, state.voiceLevel)
    val showSend = !voiceMode && (state.text.isNotBlank() || state.drafts.isNotEmpty())
    val plusRotation by animateFloatAsState(if (showActions) 45f else 0f, label = "plus-rotation")
    val border by animateColorAsState(
        if (focused) colors.accent.copy(alpha = 0.5f) else colors.divider,
        label = "input-border",
    )

    val voiceToggle: @Composable () -> Unit = {
        VoiceToggle(
            voiceMode = voiceMode,
            onClick = {
                // 切进语音模式时收起键盘，避免键盘和按住条同时占位
                if (!voiceMode) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
                voiceMode = !voiceMode
                showActions = false
            },
            onLongClick = {
                val next = VoiceStyle.entries[(voiceStyle.ordinal + 1) % VoiceStyle.entries.size]
                onVoiceStyleChange(next)
            },
        )
    }

    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // 输入栏上方的提示区
            when {
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
                        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            if (voiceMode) {
                                VoicePressBar(
                                    style = voiceStyle,
                                    recording = state.recording,
                                    intent = intent,
                                    history = history,
                                    onIntentChange = { intent = it },
                                    onStart = onVoiceStart,
                                    onRelease = { released ->
                                        intent = VoiceIntent.Send
                                        when (released) {
                                            VoiceIntent.Send -> onVoiceStop()
                                            VoiceIntent.Cancel -> onVoiceCancel()
                                            VoiceIntent.ToText -> onVoiceToText()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailing = voiceToggle,
                                )
                            } else {
                                InputField(
                                    text = state.text,
                                    border = border,
                                    onTextChange = onTextChange,
                                    onFocusChange = { isFocused ->
                                        focused = isFocused
                                        if (isFocused) showActions = false
                                    },
                                    trailing = voiceToggle,
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
                                    // 发送后收起「+」面板；键盘保留，方便继续输入
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
                            BarIcon(onClick = {
                                // 展开「+」面板时收起键盘：面板和键盘不同时占位
                                focusManager.clearFocus()
                                keyboard?.hide()
                                showActions = !showActions
                            }) {
                                Icon(
                                    Icons.Outlined.AddCircleOutline,
                                    contentDescription = stringResource(R.string.chatkit_more),
                                    tint = if (showActions) colors.accent else colors.bubbleText,
                                    modifier = Modifier.size(26.dp).rotate(plusRotation),
                                )
                            }
                        }
                    }
                    if (showActions) ActionPanel(actions = actions)
                }
            }
        }
        // 录音浮层：叠在输入栏上方，不占布局高度
        if (state.recording) {
            VoiceOverlay(
                style = voiceStyle,
                intent = intent,
                history = history,
                liveTranscript = state.liveTranscript,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** 胶囊右端的语音 / 键盘切换：44dp 点击区域。单击切换，长按循环切换语音样式。 */
@Composable
private fun VoiceToggle(voiceMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = ChatKit.colors
    Box(
        modifier = Modifier.size(44.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (voiceMode) Icons.Filled.Keyboard else Icons.Outlined.Mic,
            contentDescription = stringResource(if (voiceMode) R.string.chatkit_keyboard else R.string.chatkit_voice_toggle),
            tint = colors.bubbleText,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** 输入栏右侧的图标按钮：统一 44×56 点击区域、底部对齐到输入框。 */
@Composable
private fun BarIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(width = 44.dp, height = 56.dp)) {
        content()
    }
}

@Composable
private fun InputField(
    text: String,
    border: Color,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = ChatKit.colors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
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
            trailing()
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
