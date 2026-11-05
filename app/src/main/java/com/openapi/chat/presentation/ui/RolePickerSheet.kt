package com.openapi.chat.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.presentation.ChatUiState
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.design.theme.AppTypography
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar
import com.openapi.roles.presentation.RoleCatalogPane

private const val DEFAULT_EMOJI = "🤖"

/**
 * 对话页顶部的当前角色：头像 + 名称 + 一句提示。整条可点，点开角色面板。
 *
 * 取代原来的"切换角色"下拉条：角色是对话的主体，不该藏在一个小按钮后面。
 */
@Composable
fun RoleHeader(state: ChatUiState, onClick: () -> Unit) {
    val role = state.selectedRole
    val character = state.selectedCharacter
    val title = role?.name ?: character?.title ?: "默认助手"
    val emoji = role?.emoji ?: character?.title?.take(1) ?: DEFAULT_EMOJI
    val color = role?.colorArgb ?: AppColors.Primary.value.toLong()

    Surface(color = AppColors.Surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoleAvatar(emoji = emoji, colorArgb = color, size = 36)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = AppTypography.bodyLarge,
                    fontWeight = AppTypography.emphasisWeight,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "点击切换角色",
                    fontSize = AppTypography.caption,
                    color = AppColors.TextSecondary,
                )
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "切换角色", tint = AppColors.TextSecondary)
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
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("专业角色") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("经典角色") })
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            if (tab == 0) {
                RoleCatalogPane(selectedId = state.selectedRole?.id, onSelect = onRoleSelected)
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
                title = "默认助手",
                subtitle = "小爱，通用的聊天助手",
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
                            Icon(Icons.Filled.Edit, contentDescription = "编辑角色", tint = AppColors.TextSecondary)
                        }
                        IconButton(onClick = { onDeleteCharacter(character.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除角色", tint = AppColors.Error)
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
                Text("新建角色", color = AppColors.Primary, fontSize = AppTypography.body)
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
        RoleAvatar(emoji = emoji, colorArgb = AppColors.Primary.value.toLong(), size = 44)
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
            Icon(Icons.Filled.Check, contentDescription = "当前角色", tint = AppColors.Primary)
        }
    }
}
