package com.openapi.ks.myapp.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKitTheme
import com.openapi.feature.companion.R
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarEventMapper
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.ks.myapp.avatar.SimpleAvatarRenderer

/**
 * 陪伴页：和「对话」页长得像一家人，但有自己的角色形象与语音能力。
 *
 * 与旧版的区别（都是用户逐条提过的）：
 * 1. 去掉了「文字 / 语音」分段按钮——它切的是传输路径，用户看不懂也没有意义；
 *    真正该有的是「她怎么回」：文字 or 语音（语音时语音条 + 文字同时出现）。
 * 2. 消息区改用 chatkit 的 [ChatMessageList]，与「对话」页同一套气泡、时间戳、操作。
 * 3. 那个占屏 42%、用户不知道是什么的大框改成**可收起的形象区**，默认收起。
 * 4. 等待首字时显示「对方正在输入…」，而不是一个空气泡。
 * 5. 输入区与「对话」页一致：一个输入框 + 发送按钮，外加语音输出开关与通话入口。
 */
@Composable
fun CompanionScreen(
    viewModel: CompanionViewModel,
    characterId: String,
    modifier: Modifier = Modifier,
    /** 进全屏通话页。由 CompanionRoute 接，页面本身不持有导航。 */
    onOpenCall: () -> Unit = {},
    /** 打开角色选择。 */
    onOpenCharacters: () -> Unit = {},
    /** 打开个性化页面（记忆、重要日子、她的主动性）。 */
    onOpenProfile: () -> Unit = {},
    /** 打开抽屉。与「对话」页一致。 */
    onMenuClick: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val renderer = remember { SimpleAvatarRenderer() }
    val listState = rememberLazyListState()

    LaunchedEffect(characterId) { viewModel.start(characterId) }

    // 顶栏要显示她的真名和一句话人设，而不是 characterId（如 linche）。
    // 角色列表接口一次就能拿到，失败时退回「陪伴」标题，不阻塞页面。
    var character by remember(characterId) { mutableStateOf<CharacterSummary?>(null) }
    LaunchedEffect(characterId) {
        character = runCatching { viewModel.characterSummary(characterId) }.getOrNull()
    }

    // 绑定素材：先用本地已有的（离线也能看）或内置默认，再向服务端同步。
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val avatarSync = remember(context) { buildAvatarSync(context) }
    var portraitFile by remember(characterId) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(renderer, characterId) {
        val local = avatarSync.localFor(characterId)
        portraitFile = local?.portrait
        renderer.bind(AvatarSpec(files = local))
        val synced = runCatching { avatarSync.syncFor(characterId) }.getOrNull()
        if (synced != null && synced != local) {
            renderer.bind(AvatarSpec(files = synced))
            portraitFile = synced.portrait
        }
    }

    LaunchedEffect(state.avatarState) {
        AvatarEventMapper.mapJson("""{"type":"state","state":"${state.avatarState}"}""")
            ?.let(renderer::dispatch)
    }
    LaunchedEffect(state.emotion) {
        state.emotion?.let { renderer.dispatch(AvatarCommand.Emotion(it)) }
    }

    // 新消息到达时滚到底。用消息条数与最后一条文本做 key：流式追加时也要跟。
    // 首批历史（从没滚过）用无动画 jump：否则先画在第 0 条再 animate，用户会看到「停在第一条」。
    var pinnedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val index = state.messages.lastIndex
        if (!pinnedOnce) {
            listState.scrollToItem(index)
            pinnedOnce = true
        } else {
            listState.animateScrollToItem(index)
        }
    }

    ChatKitTheme {
        Surface(color = Color(0xFFFBF8F5), modifier = modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                CompanionTopBar(
                    name = character?.name.orEmpty(),
                    portrait = portraitFile,
                    tagline = character?.tagline.orEmpty(),
                    output = state.output,
                    expanded = state.avatarExpanded,
                    onMenuClick = onMenuClick,
                    onToggleAvatar = viewModel::toggleAvatarExpanded,
                    onCharacters = onOpenCharacters,
                    onProfile = onOpenProfile,
                    onToggleOutput = viewModel::toggleOutput,
                    onOpenCall = onOpenCall,
                )

                // 形象区：默认收起成一个头像；展开后是完整形象，占屏高但可随时收回去。
                // 旧版是固定 weight(0.42f) 常驻，用户的原话是"不知道这个大框是什么"。
                AnimatedVisibility(
                    visible = state.avatarExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SimpleAvatar(
                            renderer = renderer,
                            modifier = Modifier.fillMaxSize(),
                            // 点她一下：先给本地反馈（渲染器里的缩放弹动由 SimpleAvatar 播），
                            // 再把「碰到了哪里」告诉 agent——她该不该回一句由她决定，
                            // 客户端不替她说话（不然就成了一个假的互动）。
                            // Toast 只是点按确认；真正的回复走服务端 on_input → 回合管线。
                            onTapped = { region ->
                                viewModel.onAvatarTapped(region)
                                android.widget.Toast.makeText(
                                    context,
                                    if (region == "head") "摸了摸她的头" else "碰了她一下",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            // 嘴型：把远端音频振幅交给形象层。以前两帧嘴型画好了却没人驱动。
                            mouthLevel = viewModel.mouthLevel,
                            fallbackName = character?.name.orEmpty(),
                        )
                    }
                }

                CompanionMessageArea(
                    messages = state.messages,
                    listState = listState,
                    awaitingReply = state.awaitingReply,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onConfirmAction = viewModel::confirmAction,
                    onCancelAction = viewModel::cancelAction,
                    onDeleteMessage = viewModel::deleteMessage,
                    onCopyMessage = { copyToClipboard(context, it) },
                    onRegenerate = viewModel::regenerateLastReply,
                    onShareMessage = { shareText(context, it) },
                    onSpeakMessage = { speakText(context, it) },
                    onEditMessage = { id, _ -> viewModel.beginEdit(id) },
                )

                state.incomingCall?.let { kind ->
                    IncomingCallBanner(
                        kind = kind,
                        onAccept = {
                            viewModel.dismissIncomingCall()
                            // 走和通话按钮同一条入口：接听也要过录音权限，否则会进一个
                            // 「看着在通话、其实麦克风没发布」的页面。
                            onOpenCall()
                        },
                        onDecline = viewModel::dismissIncomingCall,
                    )
                }

                // 与「对话」页一致：错误必须看得见，否则用户不知道是没发出去还是没回复。
                state.error?.let { message ->
                    ErrorBar(message = message, onDismiss = viewModel::clearError)
                }

                CompanionComposer(
                    input = state.input,
                    canSend = state.canSend,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::send,
                )
            }
        }
    }
}

/**
 * 顶栏：菜单 · 她（头像 + 名字 + 一句话）· 语音开关 · 通话 · ⋯
 *
 * 旧版是「汉堡 + 标题 + 三个语义含糊的图标」——用户只能靠猜哪个是角色、哪个是个性化
 * （真机上踩到：「角色」用了电话图标，「个性化」用了和「展开形象」一模一样的箭头）。
 * 现在：身份区整块可点进角色选择，语音/通话是明确的常驻动作，剩下的收进 ⋯ 菜单。
 */
@Composable
private fun CompanionTopBar(
    /** 角色展示名；还没取到时为空。 */
    name: String,
    /** 头像素材；没有就退回字标。 */
    portrait: java.io.File?,
    /** 一句话人设（角色的 tagline）。 */
    tagline: String,
    output: CompanionOutput,
    expanded: Boolean,
    onMenuClick: (() -> Unit)?,
    onToggleAvatar: () -> Unit,
    onCharacters: () -> Unit,
    onProfile: () -> Unit,
    onToggleOutput: () -> Unit,
    onOpenCall: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 抽屉入口：与「对话」页同一个手势位置，切换页面时不用重新学。
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.companion_nav_back),
                    )
                }
            }
            // 身份区：整块可点 = 角色选择。头像 + 名字 + 下拉箭头，下面一行 tagline。
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCharacters() }
                    .padding(start = if (onMenuClick == null) 12.dp else 0.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarAvatar(name = name, portrait = portrait)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name.ifBlank { stringResource(R.string.companion_title) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2B2622),
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF8A8F98),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (tagline.isNotBlank()) {
                        Text(
                            text = tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8A8F98),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 语音回答：开=紫渐变底白图标，关=中性底灰图标。紫色只属于「她」。
            val voiceOn = output == CompanionOutput.VOICE
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (voiceOn) HerViolet else Color(0xFFF3EFEA))
                    .clickable { onToggleOutput() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (voiceOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (voiceOn) "语音回答：开" else "语音回答：关",
                    tint = if (voiceOn) Color.White else Color(0xFF8A8F98),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            // 通话：右侧视觉重心。由 Route 打开全屏通话页（含录音权限）。
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(HerViolet)
                    .clickable { onOpenCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = stringResource(R.string.companion_open_voice),
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(2.dp))
            // ⋯：记忆/个性化与形象区收进来。这两个入口以前是「人物」和「星星」两个图标。
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        tint = Color(0xFF6E6660),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("她记得的事与主动性") },
                        onClick = {
                            menuOpen = false
                            onProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (expanded) "收起形象" else "展开形象") },
                        onClick = {
                            menuOpen = false
                            onToggleAvatar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("换个角色") },
                        onClick = {
                            menuOpen = false
                            onCharacters()
                        },
                    )
                }
            }
        }
        // 顶栏底部一条渐变分隔线：紫 → 粉 → 暖，透明度淡出。全页唯一的装饰线。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            HerViolet.copy(alpha = 0.85f),
                            Color(0xFFFFA6C9).copy(alpha = 0.55f),
                            Color(0xFFFBF8F5).copy(alpha = 0f),
                        ),
                    ),
                ),
        )
    }
}

/** 紫色只表达「她」：头像环、通话、语音开关的开启态。全模块共享（角色列表也要用）。 */
internal val HerViolet = Color(0xFF8B7CF6)

@Composable
private fun TopBarAvatar(name: String, portrait: java.io.File?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(HerViolet.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (portrait != null) {
            com.openapi.ks.myapp.avatar.AvatarPortrait(
                file = portrait,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.trim().take(1).ifBlank { "她" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6F5BD6),
            )
        }
    }
}

@Composable
private fun CompanionAvatar(emoji: String, active: Boolean) {
    // 头像先做一个圆形字标：真实形象是 SimpleAvatar 那套素材，消息列表里放不下也读不清，
    // 这里只承担"是谁在说话"的识别功能。
    val alpha = if (active) 1f else 0.9f
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 用户侧头像：中性底的「我」字标。
 *
 * 现在没有账号头像图片源，字标是唯一能落地的形态。刻意用中性暖灰而不是品牌主色——
 * 紫色/橙色各代表「她」和「发送」，用户的头像不该抢这两个语义。
 */
@Composable
private fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFFEDE6DC)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "我",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8A7C6A),
        )
    }
}

/**
 * 消息区：列表 + 「正在输入」 + 待确认动作卡片。
 *
 * 单独抽出来是为了能被渲染测试直接组合（见 app 模块的 CompanionMessageAreaRenderTest）：
 * 「确认按钮还在不在」这类回归只有在真树上断言才发现得了，状态层测试删了控件照样过。
 * 参数全是纯数据与回调，不依赖 ViewModel。public 是因为测试在 :app 模块里，
 * 同模块的 internal 跨不过模块边界。
 */
@Composable
fun CompanionMessageArea(
    messages: List<CompanionMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    awaitingReply: Boolean,
    modifier: Modifier = Modifier,
    onConfirmAction: (String) -> Unit = {},
    onCancelAction: (String) -> Unit = {},
    onDeleteMessage: (Long) -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onSpeakMessage: (String) -> Unit = {},
    onEditMessage: (Long, String) -> Unit = { _, _ -> },
) {
    Column(modifier) {
        val items = remember(messages) { companionItems(messages) }
        // 消息 id → 领域消息。长按弹层里的操作要作用到原来的那条，而不是列表下标。
        val byId = remember(messages) { messages.associateBy { "c${it.id}" } }
        // 主动消息的来源标签走 chatkit 的 itemOverlay 插槽：ChatItem 没有「谁先开口」
        // 这个概念，而标签必须挂在**那一条**消息上方才读得懂，所以既不放列表下方，
        // 也不塞进正文（正文会被复制走）。插槽默认空实现，对话页布局不受影响。
        val proactiveKinds = remember(messages) { messages.associate { "c${it.id}" to it.proactive } }
        ChatMessageList(
            items = items,
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 与「对话」页同一套长按弹层，而不是横排小卡片：一行一项，图标和文字都看得清。
            menuStyle = com.openapi.chatkit.message.MessageMenuStyle.Sheet,
            itemOverlay = { item ->
                val kind = proactiveKinds[item.id]
                if (kind != null && kind != ProactiveKind.REPLY) ProactiveLabel(kind)
            },
            footerActions = {
                // 气泡下面不再挂 footer 芯片：长按弹层里全部都有，重复摆一遍只是噪音。
                // chatkit 在 footerActions 为空时本就不渲染这一块。
                emptyList()
            },
            onAction = { item, action ->
                val message = byId[item.id] ?: return@ChatMessageList
                when (action) {
                    MessageAction.Copy -> onCopyMessage(message.text)
                    MessageAction.Delete -> onDeleteMessage(message.id)
                    // 「重新回答」出现在「最后一条是用户消息」时（删掉她的回复后会露出来），
                    // 必须真的能重问，否则又是一个点了没反应的按钮。
                    MessageAction.Regenerate -> onRegenerate()
                    // 长按菜单里的这三项以前落到 else -> Unit，点了毫无反应。
                    MessageAction.Share -> if (message.text.isNotBlank()) onShareMessage(message.text)
                    MessageAction.Speak -> if (message.text.isNotBlank()) onSpeakMessage(message.text)
                    // 编辑：把原文放回输入框并从列表移除（服务端没有单条删除接口，与对话页语义一致）
                    MessageAction.Edit -> if (message.text.isNotBlank()) {
                        onEditMessage(message.id, message.text)
                    }
                    else -> Unit
                }
            },
            avatar = { item -> CompanionAvatar(emoji = "澈", active = item.streaming) },
            // 用户侧头像：现在没有账号头像图片源，用中性色的「我」字标。
            // 与她的头像刻意不同色（她是品牌主色），一眼能分出左右两边是谁。
            // 以后账号体系有头像 URL 了，只换这个 composable。
            userAvatar = { UserAvatar() },
            // 空列表时不放文字：下方输入框自己就有同一个占位提示，
            // 否则屏幕上会出现两行一模一样的「说点什么…」。
            emptyContent = {},
        )

        // 等待首字：她还没开始说话。用「正在输入」而不是一个空气泡——空气泡看起来像卡住了。
        if (awaitingReply) TypingHint()

        // 待确认动作：独立卡片区，放在消息流**下方**（输入框上方）。
        // 理由：确认是用户马上去做的决定，不是一段聊天记录。混在消息流里会被后来的
        // 消息冲走、翻上去就看不见了；固定在这里则一直在视线内，而且不用改 chatkit
        // 的 ChatItem（它没有这个语义）。只显示最新一条待确认。
        messages.lastOrNull { it.confirmationId != null }?.confirmationId?.let { id ->
            val pending = messages.last { it.confirmationId == id }
            ConfirmationCard(
                text = pending.text,
                onConfirm = { onConfirmAction(id) },
                onCancel = { onCancelAction(id) },
            )
        }
    }
}

/**
 * 主动消息的来源标签，挂在**那一条**消息的上方（由 ChatMessageList 的 itemOverlay 渲染）。
 *
 * 为什么不写进正文：正文会被「复制」带走，用户粘贴到别处时不该出现「她记得」这种界面标签；
 * 也不做成列表下方的一条横幅——标签必须紧挨着它修饰的那句话才有意义。
 */
@Composable
private fun ProactiveLabel(kind: ProactiveKind) {
    Text(
        text = stringResource(
            when (kind) {
                ProactiveKind.EVENT -> R.string.companion_proactive_event
                ProactiveKind.GREETING -> R.string.companion_proactive_greeting
                else -> R.string.companion_proactive_generic
            }
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        // 留白对齐：与 chatkit 的消息行同一起点（头像 28dp + 间距 8dp），标签才不会飘在气泡外面。
        modifier = Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 2.dp),
    )
}

/**
 * 待确认动作的卡片：她要替用户做一件事，用户必须先点头。
 *
 * 放在消息列表下方、输入框上方，而不是消息流里：
 * 1. 这是**此刻**要做的决定，不是历史记录，翻上去就丢了的按钮等于没有按钮；
 * 2. 不占消息流的空间，也不参与「连续同一发送者才显示头像」这类排版逻辑；
 * 3. chatkit 的 ChatItem 没有这个语义，硬塞进去会让对话页也背上陪伴页的概念。
 */
@Composable
private fun ConfirmationCard(text: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (text.isNotBlank()) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.companion_action_confirm))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.companion_action_cancel))
                }
            }
        }
    }
}

/** 复制一条消息。必须有可见反馈：用户点了没反应会以为按钮坏了。 */
private fun copyToClipboard(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("companion", text))
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.companion_copied),
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

/**
 * 分享一段文字：系统选择器。
 *
 * 「对话」页的 ChatPlatform.share 是 feature-chat 的 internal object，跨模块不可见；
 * 为一个 Intent 建公共模块不值得，这里就地写十行。
 */
private fun shareText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(intent, null)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * 朗读一段文字：设备自带中文 TTS，「对话」页走的是同一个实现。
 * 设备没有可用中文语音时回一句提示，而不是静默失败——否则用户会以为按钮又坏了。
 */
private fun speakText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val app = context.applicationContext
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).also { scope ->
        scope.launch {
            val ok = runCatching { com.openapi.speech.tts.DeviceSpeech.speakChinese(app, text) }
                .getOrDefault(false)
            if (!ok) {
                android.widget.Toast.makeText(
                    app,
                    "设备没有可用的中文语音",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

/** 「对方正在输入…」：三个点依次跳动。 */
@Composable
private fun TypingHint() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "对方正在输入",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(3) { index ->
            val dotAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha),
            )
        }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("关闭", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 输入区（方案 A：与「对话」页同一套视觉语言）。
 *
 * 白卡 + 无边框 `BasicTextField` + 有字才出现的橙色圆形发送按钮。
 * 「语音回答」与「通话」已经移到顶栏，这一行只剩输入本身。
 *
 * 为什么不用 chatkit 的 `ChatComposer`：那是带录音/转写/草稿/「+」面板的完整组件，
 * 需要 `ComposerState/ComposerAction/VoiceActions` 一整套回调，而陪伴侧的麦克风链路
 * 才刚在通话页补上，接进来等于一个按不动的语音按钮。等语音链路稳定后再评估。
 */
@Composable
private fun CompanionComposer(
    input: String,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val sendEnabled = canSend
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
            .shadow(elevation = 8.dp, shape = ComposerShape, clip = false)
            .background(Color.White, ComposerShape)
            .border(
                width = 1.dp,
                color = Color(0x14000000),
                shape = ComposerShape,
            )
            .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF2B2622),
                    fontSize = 15.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC2410C)),
                maxLines = 4,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.companion_input_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 15.sp,
                                color = Color(0xFFB3AEA8),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.size(8.dp))
            // 发送按钮：有字才出现（缩放进出），空输入时整块收起，不留一个灰疙瘩。
            AnimatedVisibility(
                visible = input.isNotBlank(),
                enter = scaleIn(spring(dampingRatio = 0.7f)) + fadeIn(),
                exit = scaleOut(spring(dampingRatio = 0.7f)) + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (sendEnabled) Color(0xFFC2410C) else Color(0xFFE7DED6))
                        .clickable(enabled = sendEnabled) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.companion_send),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 输入卡片的圆角：24dp，与设计稿一致。 */
private val ComposerShape = RoundedCornerShape(24.dp)

@Composable
private fun IncomingCallBanner(
    kind: ProactiveKind,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.companion_incoming_call),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAccept) { Text(stringResource(R.string.companion_call_accept)) }
                TextButton(onClick = onDecline) { Text(stringResource(R.string.companion_call_decline)) }
            }
        }
    }
}

/**
 * 陪伴页消息 → chatkit 的 UI 模型。
 *
 * 不需要映射附件/语音：陪伴页的消息只有文字（语音条要等服务端在事件里带上分句与时长）。
 * 主动消息的来源标签与待确认动作由 [ChatMessageList] 的 overlay 插槽、以及列表下方的
 * 独立确认卡片承担——chatkit 的 ChatItem 没有这两个概念，也不该为它们改动数据模型。
 */
private fun companionItems(messages: List<CompanionMessage>): List<ChatItem> = messages.map { m ->
    val key = "c${m.id}"
    ChatItem(
        id = key,
        key = key,
        sender = if (m.role == MessageRole.USER) Sender.User else Sender.Assistant,
        parts = listOf(MessagePart.Text(m.text)).filter { m.text.isNotEmpty() },
        // 占位消息（还没有任何字）不画气泡：它由下方的「正在输入」指示器代替。
        // 空文本的流式占位气泡**不**交给 chatkit 的 thinking：
        // 列表下方已经有「对方正在输入…」那一条，两个指示器同时出现等于同一句话说两遍。
        thinking = false,
        streaming = m.streaming && m.text.isNotEmpty(),
    )
}

private fun buildAvatarSync(context: android.content.Context): com.openapi.ks.myapp.avatar.AvatarSync {
    @Suppress("DEPRECATION")
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionCode }
        .getOrDefault(0)
    return com.openapi.ks.myapp.avatar.AvatarSync(
        baseUrl = CompanionConfig.baseUrl,
        tokenProvider = {
            com.openapi.common.util.android.PreferenceUtils.getPrefString(
                context, com.openapi.common.net.http.NetConfig.tokenHeader(), null)
        },
        client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
        store = com.openapi.ks.myapp.avatar.AvatarPackStore(context),
        cacheDir = context.cacheDir,
        appVersion = version,
    )
}
