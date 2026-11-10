@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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

private val CardShape = RoundedCornerShape(16.dp)
private val IconInk = Color(0xFF1F1F1F)
private val SendBlue = Color(0xFF0066FF)
private val SendBlueDisabled = Color(0xFFB3CCFF)

/**
 * 对话输入栏（豆包式）：一张白色圆角卡片，所有东西都在卡片里。
 *
 * - 语音模式：[相机] 按住说话（居中） [键盘] [+]
 * - 文字模式、无文字：[相机] 输入框 [麦克风] [+]
 * - 文字模式、有文字或草稿：输入框 [蓝色发送箭头]；生成中换成停止按钮
 * - 草稿托盘在卡片内、输入行上方；「+」会收起键盘并展开 [actions] 面板。
 *
 * 豆包式按住录音时卡片隐藏（保留布局高度，不让列表跳动），由 [VoiceOverlay] 在底部画光晕。
 * 微信式（第二阶段）仍保留旧的卡片浮层和左右滑手势。
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
    /** 录音中松手去向变了（发送 / 取消 / 转文字）。随后会震动一次，录音方可借此把震动那一小段静音。 */
    onVoiceIntentChange: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onVoiceDraftSendText: (String) -> Unit = {},
    onVoiceDraftSendAudio: () -> Unit = {},
    onVoiceDraftCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var voiceMode by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf(VoiceIntent.Send) }
    val history = rememberLevelHistory(state.recording, state.voiceLevel)
    val showSend = !voiceMode && (state.text.isNotBlank() || state.drafts.isNotEmpty())
    val plusRotation by animateFloatAsState(if (showActions) 45f else 0f, label = "plus-rotation")
    val camera = actions.firstOrNull { it.id == "camera" }
    // 豆包式录音时卡片整体隐藏，光晕替代它；透明度不影响手势，所以按住的手势不会断
    val hideCard = state.recording && voiceStyle == VoiceStyle.Ripple

    val toggleVoice = {
        // 切进语音模式时收起键盘，避免键盘和按住区同时占位
        if (!voiceMode) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
        voiceMode = !voiceMode
        showActions = false
    }
    val longPressStyle = {
        val next = VoiceStyle.entries[(voiceStyle.ordinal + 1) % VoiceStyle.entries.size]
        onVoiceStyleChange(next)
    }

    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // 输入栏上方的提示区
            if (state.transcribing) {
                Hint(text = stringResource(R.string.chatkit_transcribing), emphasized = true)
            }
            // 转文字编辑态：识别文字 + 发送原语音 / 发送 / 取消
            state.voiceDraftText?.let { draftText ->
                VoiceDraftEditor(
                    initialText = draftText,
                    onSendText = onVoiceDraftSendText,
                    onSendAudio = onVoiceDraftSendAudio,
                    onCancel = onVoiceDraftCancel,
                )
            }
            // 编辑提示条：在输入卡片上方，× 退出编辑
            if (state.editing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.chatkit_editing_banner),
                        modifier = Modifier.weight(1f),
                        fontSize = ChatKit.typography.secondary,
                        color = colors.secondaryText,
                    )
                    IconButton(onClick = onCancelEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.chatkit_cancel_edit),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                InputCard(modifier = Modifier.graphicsLayer { alpha = if (hideCard) 0f else 1f }) {
                    if (state.drafts.isNotEmpty()) {
                        DraftTray(drafts = state.drafts, onRemove = onRemoveDraft)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // 左：相机（有文字时收起，给输入框让位）
                        if (camera != null && !showSend) {
                            IconSlot(onClick = camera.onClick) {
                                Icon(camera.icon, contentDescription = camera.label, tint = IconInk, modifier = Modifier.size(24.dp))
                            }
                        }

                        // 中：按住说话 / 输入框
                        if (voiceMode) {
                            VoicePressBar(
                                style = voiceStyle,
                                recording = state.recording,
                                intent = intent,
                                history = history,
                                onIntentChange = {
                                    intent = it
                                    onVoiceIntentChange()
                                },
                                onStart = onVoiceStart,
                                onRelease = { released ->
                                    intent = VoiceIntent.Send
                                    when (released) {
                                        VoiceIntent.Send -> onVoiceStop()
                                        VoiceIntent.Cancel -> onVoiceCancel()
                                        VoiceIntent.ToText -> onVoiceToText()
                                    }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            )
                        } else {
                            InputField(
                                text = state.text,
                                onTextChange = onTextChange,
                                onFocusChange = { isFocused -> if (isFocused) showActions = false },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // 右：语音 / 键盘切换
                        if (voiceMode) {
                            IconSlot(onClick = toggleVoice, onLongClick = longPressStyle) {
                                KeyboardGlyph()
                            }
                        } else if (!showSend) {
                            IconSlot(onClick = toggleVoice, onLongClick = longPressStyle) {
                                Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.chatkit_voice_toggle), tint = IconInk, modifier = Modifier.size(24.dp))
                            }
                        }

                        // 右：+ / 发送 / 停止
                        when {
                            state.generating && !voiceMode -> IconSlot(onClick = onStop) {
                                // 生成中：蓝色圆形「停止」，点一下中断，已生成的内容保留
                                Box(
                                    Modifier.size(28.dp).background(SendBlue, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(10.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                }
                            }
                            showSend -> IconSlot(
                                onClick = {
                                    // 发送后收回键盘和「+」面板
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    showActions = false
                                    if (state.canSend) onSend()
                                },
                            ) {
                                // 蓝色实心圆 + 白色向上箭头
                                Box(
                                    Modifier.size(28.dp).background(if (state.canSend) SendBlue else SendBlueDisabled, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.chatkit_send), tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            else -> IconSlot(
                                onClick = {
                                    // 展开「+」面板时收起键盘：面板和键盘不同时占位
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    showActions = !showActions
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.AddCircleOutline,
                                    contentDescription = stringResource(R.string.chatkit_more),
                                    tint = IconInk,
                                    modifier = Modifier.size(26.dp).rotate(plusRotation),
                                )
                            }
                        }
                    }
                }
            }
            if (showActions) {
                Box(Modifier.fillMaxWidth().background(colors.surfaceVariant)) {
                    ActionPanel(actions = actions)
                }
            }
        }
        // 录音浮层：豆包式对齐到底边（光晕延伸到屏幕底部），微信式对齐到顶边
        if (state.recording) {
            VoiceOverlay(
                style = voiceStyle,
                intent = intent,
                history = history,
                liveTranscript = state.liveTranscript,
                modifier = Modifier.align(if (voiceStyle == VoiceStyle.Ripple) Alignment.BottomStart else Alignment.TopStart),
            )
        }
    }
}

/** 白色圆角卡片：16dp 圆角、柔和阴影。 */
@Composable
private fun InputCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = CardShape, ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
            .background(Color.White, CardShape),
        content = content,
    )
}

/** 卡片里的图标位：44×56dp 点击区，图标居中。 */
@Composable
private fun IconSlot(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 56.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 键盘图标：圆圈里一个键盘（对应豆包的圆形线框图标）。 */
@Composable
private fun KeyboardGlyph() {
    Box(
        Modifier
            .size(26.dp)
            .border(1.5.dp, IconInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Keyboard,
            contentDescription = stringResource(R.string.chatkit_keyboard),
            tint = IconInk,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 文字输入框：卡片内，17sp，最多 5 行，随内容向上长高。 */
@Composable
private fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    Box(
        modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(fontSize = 17.sp, color = Color.Black),
            cursorBrush = SolidColor(colors.accent),
            maxLines = 5,
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.chatkit_input_hint), fontSize = 17.sp, color = Color(0xFF9CA3AF))
                }
                inner()
            },
        )
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
