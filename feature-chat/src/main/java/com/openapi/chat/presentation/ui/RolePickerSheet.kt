package com.openapi.chat.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.presentation.ChatUiState
import com.openapi.common.design.theme.AppColors
import com.openapi.common.design.theme.AppSpacing
import com.openapi.common.design.theme.AppTypography
import com.openapi.feature.chat.R
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar
import com.openapi.roles.presentation.RoleCatalogPane

private const val DEFAULT_EMOJI = "🤖"

/**
 * 0xAARRGGBB 的 Long，和 RoleColors 的输出是同一种表示。
 * 不能用 Color.value：Compose 把颜色放在 ULong 的高 32 位，直接 toLong() 得到的不是 ARGB。
 */
private val PrimaryArgb: Long = AppColors.Primary.toArgb().toLong() and 0xFFFFFFFFL

/** 当前对话的"助手"是谁：头部、空状态、助手气泡旁的头像都从这里取，保证三处一致。 */
internal data class AssistantIdentity(
    val name: String,
    val emoji: String,
    val colorArgb: Long,
    /** 默认助手使用旧版头像图；选了角色后为 null，显示角色自己的 emoji。 */
    val avatarRes: Int? = null,
)

@Composable
internal fun ChatUiState.assistantIdentity(): AssistantIdentity {
    val role = selectedRole
    val character = selectedCharacter
    val defaultName = stringResource(R.string.roles_default_title)
    return AssistantIdentity(
        name = role?.name ?: character?.title ?: defaultName,
        emoji = role?.emoji ?: character?.title?.take(1) ?: DEFAULT_EMOJI,
        colorArgb = role?.colorArgb ?: PrimaryArgb,
        avatarRes = if (role == null && character == null) R.drawable.ai_chat_1 else null,
    )
}

/**
 * 对话页顶部的当前角色：头像 + 名称。整条可点，点开角色面板。
 *
 * 取代原来的stringResource(R.string.roles_switch_desc)下拉条：角色是对话的主体，不该藏在一个小按钮后面。
 */
@Composable
fun RoleHeader(
    state: ChatUiState,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onSessionsClick: () -> Unit,
    /** 非空时在最左边显示菜单按钮（主页用它打开侧边抽屉）。 */
    onMenuClick: (() -> Unit)? = null,
) {
    val identity = state.assistantIdentity()
    val title = identity.name
    val emoji = identity.emoji
    val color = identity.colorArgb

    // 顶栏用品牌色，状态栏顺着它延伸（statusBarsPadding 把内容推到状态栏下面，底色仍由 Surface 画）
    Surface(color = AppColors.Primary, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .clickable(onClick = onClick)
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.home_menu),
                        tint = Color.White,
                    )
                }
            }
            RoleAvatar(emoji = emoji, colorArgb = color, size = 36, imageRes = identity.avatarRes)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = AppTypography.bodyLarge,
                    fontWeight = AppTypography.emphasisWeight,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 副标题「点击切换角色」已经说明了操作，不再额外放一个下拉箭头
            // 会话入口：切换、新建、删除对话（替代旧的会话列表页）
            IconButton(onClick = onSessionsClick) {
                Icon(Icons.Filled.List, contentDescription = stringResource(R.string.chat_sessions_desc), tint = Color.White)
            }
            // 通话入口放在头部：通话的对象就是当前角色，以后 LiveKit agent 直接读同一个角色
            IconButton(onClick = onCallClick) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.chat_call_desc), tint = Color.White)
            }
        }
    }
}

/**
 * 角色面板：两个标签。
 * - 专业角色：角色目录（搜索、分类、分页），服务端 /api/roles/v1。
 * - 经典角色：默认助手和旧的人设（含用户自己创建的，可编辑/删除），服务端旧接口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolePickerSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onRoleSelected: (SelectedRole) -> Unit,
    onCharacterSelected: (Int?) -> Unit,
    onCreateCharacter: (title: String, content: String) -> Unit,
    onUpdateCharacter: (id: Int, title: String, content: String) -> Unit,
    onDeleteCharacter: (id: Int) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        TabRow(selectedTabIndex = tab, containerColor = AppColors.Surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.roles_tab_pro)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.roles_tab_classic)) })
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            if (tab == 0) {
                Column(Modifier.fillMaxSize()) {
                    // 默认助手（通用角色）必须始终能选：固定在专业角色列表最上面，不依赖服务端目录
                    ClassicRow(
                        title = stringResource(R.string.roles_default_title),
                        subtitle = stringResource(R.string.roles_default_subtitle),
                        emoji = DEFAULT_EMOJI,
                        selected = state.selectedRole == null && state.selectedCharacterId == null,
                        onClick = { onCharacterSelected(null) },
                    )
                    HorizontalDivider(color = AppColors.Divider)
                    Box(Modifier.weight(1f)) {
                        RoleCatalogPane(selectedId = state.selectedRole?.id, onSelect = onRoleSelected)
                    }
                }
            } else {
                ClassicRolesPane(
                    characters = state.characters,
                    // 选了目录角色时，经典页里没有任何一项是"当前"
                    defaultSelected = state.selectedRole == null && state.selectedCharacterId == null,
                    selectedCharacterId = if (state.selectedRole == null) state.selectedCharacterId else null,
                    onCharacterSelected = onCharacterSelected,
                    onCreateCharacter = onCreateCharacter,
                    onUpdateCharacter = onUpdateCharacter,
                    onDeleteCharacter = onDeleteCharacter,
                )
            }
        }
    }
}

@Composable
private fun ClassicRolesPane(
    characters: List<ChatCharacterOption>,
    defaultSelected: Boolean,
    selectedCharacterId: Int?,
    onCharacterSelected: (Int?) -> Unit,
    onCreateCharacter: (title: String, content: String) -> Unit,
    onUpdateCharacter: (id: Int, title: String, content: String) -> Unit,
    onDeleteCharacter: (id: Int) -> Unit,
) {
    // null 表示新建；非 null 表示在编辑这个角色
    var editing by remember { mutableStateOf<ChatCharacterOption?>(null) }
    var showEdit by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ClassicRow(
                title = stringResource(R.string.roles_default_title),
                subtitle = stringResource(R.string.roles_default_subtitle),
                emoji = DEFAULT_EMOJI,
                selected = defaultSelected,
                onClick = { onCharacterSelected(null) },
            )
        }
        items(characters, key = { it.id }) { character ->
            ClassicRow(
                title = character.title,
                subtitle = character.preview,
                emoji = character.title.take(1),
                selected = character.id == selectedCharacterId,
                onClick = { onCharacterSelected(character.id) },
                trailing = if (character.mine) {
                    {
                        // 编辑/删除只对自己创建的角色开放；官方角色没有这两个按钮
                        IconButton(onClick = { editing = character; showEdit = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_edit_role), tint = AppColors.TextSecondary)
                        }
                        IconButton(onClick = { onDeleteCharacter(character.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_delete_role), tint = AppColors.Error)
                        }
                    }
                } else {
                    null
                },
            )
        }
        item {
            HorizontalDivider(color = AppColors.Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = null; showEdit = true }
                    .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.Primary)
                Spacer(Modifier.width(AppSpacing.sm))
                Text(stringResource(R.string.ui_new_role), color = AppColors.Primary, fontSize = AppTypography.body)
            }
        }
    }

    if (showEdit) {
        CharacterEditDialog(
            initial = editing,
            onDismiss = { showEdit = false },
            onConfirm = { title, content ->
                val target = editing
                if (target == null) onCreateCharacter(title, content) else onUpdateCharacter(target.id, title, content)
                showEdit = false
            },
        )
    }
}

@Composable
private fun ClassicRow(
    title: String,
    subtitle: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleAvatar(emoji = emoji, colorArgb = PrimaryArgb, size = 44)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = AppTypography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = AppTypography.caption,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.ui_current_role), tint = AppColors.Primary)
        }
    }
}
