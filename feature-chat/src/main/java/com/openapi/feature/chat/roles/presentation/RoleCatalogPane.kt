package com.openapi.feature.chat.roles.presentation

import androidx.compose.ui.res.stringResource
import com.openapi.feature.chat.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.ui.theme.AppSpacing
import com.openapi.common.ui.theme.AppTypography
import com.openapi.feature.chat.roles.domain.RoleSummary
import com.openapi.feature.chat.roles.domain.SelectedRole

/**
 * 角色目录的选择列表：搜索 + 分类筛选 + 分页。
 *
 * 一个角色一行：左边是 emoji 头像（底色取角色自己的颜色），中间是名称和简介，
 * 当前选中的打勾。点击即选中，由调用方决定选中后做什么（关闭面板、写入对话）。
 */
@Composable
fun RoleCatalogPane(
    selectedId: String?,
    onSelect: (SelectedRole) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RolePickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // 滚到接近底部时加载下一页
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.roles_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            item {
                FilterChip(
                    selected = state.filter == RoleFilter.Featured,
                    onClick = { viewModel.onFilterChange(RoleFilter.Featured) },
                    label = { Text(stringResource(R.string.roles_filter_featured)) },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == RoleFilter.All,
                    onClick = { viewModel.onFilterChange(RoleFilter.All) },
                    label = { Text(stringResource(R.string.ui_all)) },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == RoleFilter.Favorites,
                    onClick = { viewModel.onFilterChange(RoleFilter.Favorites) },
                    label = { Text(stringResource(R.string.roles_filter_favorites)) },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == RoleFilter.Recent,
                    onClick = { viewModel.onFilterChange(RoleFilter.Recent) },
                    label = { Text(stringResource(R.string.roles_filter_recent)) },
                )
            }
            items(state.divisions, key = { it.id }) { division ->
                FilterChip(
                    selected = (state.filter as? RoleFilter.Division)?.id == division.id,
                    onClick = { viewModel.onFilterChange(RoleFilter.Division(division.id)) },
                    label = { Text("${division.label} ${division.count}") },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.failed && state.items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.roles_load_failed), color = AppColors.TextSecondary, fontSize = AppTypography.body)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.ui_retry)) }
                }

                state.items.isEmpty() -> Text(
                    emptyText(state),
                    color = AppColors.TextSecondary,
                    fontSize = AppTypography.body,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { role ->
                        RoleRow(
                            role = role,
                            selected = role.id == selectedId,
                            favorite = role.id in state.favoriteIds,
                            onClick = {
                                viewModel.onRolePicked(role)
                                onSelect(SelectedRole(role.id, role.name, role.emoji, role.colorArgb))
                            },
                            onToggleFavorite = { viewModel.onToggleFavorite(role) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(AppSpacing.lg), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun emptyText(state: RolePickerState): String = when (state.filter) {
    RoleFilter.Favorites -> stringResource(R.string.roles_empty_favorites)
    RoleFilter.Recent -> stringResource(R.string.roles_empty_recent)
    else -> stringResource(R.string.roles_empty_search)
}

@Composable
private fun RoleRow(
    role: RoleSummary,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleAvatar(emoji = role.emoji, colorArgb = role.colorArgb, size = 44)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                role.name,
                fontSize = AppTypography.bodyLarge,
                fontWeight = AppTypography.emphasisWeight,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (role.description.isNotBlank()) {
                Text(
                    role.description,
                    fontSize = AppTypography.caption,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.ui_current_role), tint = AppColors.Primary)
        }
        Text(
            text = if (favorite) "★" else "☆",
            fontSize = AppTypography.bodyLarge,
            color = if (favorite) AppColors.Primary else AppColors.TextSecondary,
            modifier = Modifier
                .padding(start = AppSpacing.sm)
                .clickable(onClick = onToggleFavorite)
                .padding(AppSpacing.xs),
        )
    }
}

/** emoji 头像：圆形，底色是角色颜色的淡化版，保证 emoji 在任何颜色上都看得清。 */
@Composable
fun RoleAvatar(
    emoji: String,
    colorArgb: Long,
    size: Int,
    modifier: Modifier = Modifier,
    /** 非空时直接显示这张图（默认助手用旧版头像），否则显示 emoji。 */
    imageRes: Int? = null,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(colorArgb).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageRes != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(imageRes),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        } else {
            Text(
                text = emoji.ifBlank { "🤖" },
                fontSize = (size * 0.5f).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
