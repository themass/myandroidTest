@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
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
 * 对话输入栏（豆包式）：一张白色圆角卡片，所有东西都在卡片里。
 *
 * - 语音模式：[相机] 按住说话（居中） [键盘] [+]
 * - 文字模式、无文字：[相机] 输入框 [麦克风] [+]
 * - 文字模式、有文字或草稿：输入框 [蓝色发送箭头]。
 *   **A36①：发送键常驻**——生成中也照常可点（纯文字走 followup 队列，回合结束自动续发），
 *   输入栏上方的「N 条排队中」徽标可点开逐条撤回；停止生成不再占用发送键位
 *   （停止入口移到生成中的气泡上，见 ChatMessageItem）。
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
    onRemoveDraft: (String) -> Unit,
    /** 长按草稿托盘里的图片（S3「再编辑」）。不传 = 该页不提供编辑。 */
    onEditDraftImage: (String) -> Unit = {},
    /** 从「N 条排队中」徽标里撤回第 [index] 条（A36①）。 */
    onRemoveQueued: (Int) -> Unit = {},
    /** A40：「立刻发出」——把第 [index] 条排队文字马上提交服务端（不等当前回合结束）。 */
    onPromoteQueued: (Int) -> Unit = {},
    /** A40：「打断重发」——把第 [index] 条插到队首并取消当前回合。 */
    onInterruptQueued: (Int) -> Unit = {},
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
    // A36①：「N 条排队中」徽标点开后的展开态（可逐条撤回）
    var queueExpanded by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf(VoiceIntent.Send) }
    val history = rememberLevelHistory(state.recording, state.voiceLevel)
    // A36①：发送键常驻——纯文字生成中也能按（走排队）；生成中的纯草稿不显示发送键，
    // 因为发送闸会把纯附件消息挡到回合结束（feature-chat 的 ChatTurnGate），点了没结果
    // 比不给按更容易被当成按钮坏了。判定在 ComposerState.sendVisible，有单测。
    val showSend = !voiceMode && state.sendVisible
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
            // A36①：followup 队列徽标——生成中按发送的纯文字在排队（回合结束自动续发），
            // 点徽标展开可逐条撤回。发送键始终在键位上；停止入口已移到生成中的气泡上（A36②）。
            if (state.queuedCount > 0) {
                QueuedBadge(
                    count = state.queuedCount,
                    texts = state.queued,
                    expanded = queueExpanded,
                    onToggle = { queueExpanded = !queueExpanded },
                    onRemove = { index ->
                        onRemoveQueued(index)
                        // 撤回最后一条后收起：不留一个空展开态
                        if (state.queuedCount == 1) queueExpanded = false
                    },
                    onPromote = onPromoteQueued,
                    onInterrupt = onInterruptQueued,
                )
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
                        DraftTray(drafts = state.drafts, onRemove = onRemoveDraft, onEditImage = onEditDraftImage)
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

                        // 右：+ / 发送。A36：发送键常驻（生成中也走排队），
                        // 停止入口不在这个键位上——已移到生成中的气泡（见 ChatMessageItem）。
                        when {
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

/**
 * A36① 的「N 条排队中」徽标：点一下展开成排队内容清单，每条带「撤回」。
 *
 * 折叠态只占一行——它必须一直看得见（用户得知道刚按的发送进了队列而不是丢了），
 * 但不能把输入框顶走，所以展开态限高可滚。撤回落回输入框的活儿由调用方决定
 * （feature-chat 直接丢回 input；app/agent 的独立页自己管）。
 *
 * A40：每条还有两个「现在就要答案」的出口——「立刻发出」（不等当前回合，服务端裁决
 * 合并/插话/排队）与「打断重发」（取消当前回合，优先回这条）。三个动作都按 index
 * 回调；按钮分两行放（文字一行、动作一行），411dp 宽塞三行内联按钮会互相挤。
 */
@Composable
internal fun QueuedBadge(
    count: Int,
    texts: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: (Int) -> Unit,
    onPromote: (Int) -> Unit = {},
    onInterrupt: (Int) -> Unit = {},
) {
    val colors = ChatKit.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chatkit_queued_count, count),
                modifier = Modifier.weight(1f),
                fontSize = ChatKit.typography.body,
                color = colors.bubbleText,
            )
            Text(
                stringResource(if (expanded) R.string.chatkit_queue_collapse else R.string.chatkit_queue_expand),
                fontSize = ChatKit.typography.caption,
                color = colors.accent,
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                texts.forEachIndexed { index, text ->
                    Column {
                        Text(
                            text,
                            fontSize = ChatKit.typography.secondary,
                            color = colors.secondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            QueuedAction(
                                label = stringResource(R.string.chatkit_queue_send_now),
                                tag = "queued-promote-$index",
                                onClick = { onPromote(index) },
                            )
                            QueuedAction(
                                label = stringResource(R.string.chatkit_queue_interrupt),
                                tag = "queued-interrupt-$index",
                                onClick = { onInterrupt(index) },
                            )
                            QueuedAction(
                                label = stringResource(R.string.chatkit_queue_withdraw),
                                tag = "queued-withdraw-$index",
                                onClick = { onRemove(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 徽标展开清单里每行的一个小动作按钮（A40：立刻发出 / 打断重发 / 撤回 共用）。 */
@Composable
private fun QueuedAction(label: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    ) {
        Text(
            label,
            fontSize = ChatKit.typography.caption,
            color = ChatKit.colors.accent,
        )
    }
}
