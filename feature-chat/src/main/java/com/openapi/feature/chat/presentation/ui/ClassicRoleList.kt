package com.openapi.feature.chat.presentation.ui

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
import com.openapi.feature.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.presentation.ChatUiState
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.ui.theme.AppSpacing
import com.openapi.common.ui.theme.AppTypography
import com.openapi.feature.chat.R
import com.openapi.feature.chat.roles.domain.SelectedRole
import com.openapi.feature.chat.roles.presentation.RoleAvatar
import com.openapi.feature.chat.roles.presentation.RoleCatalogPane

@Composable
internal fun ClassicRolesPane(
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
internal fun ClassicRow(
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
