package com.openapi.ks.myapp.content.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.openapi.uikit.theme.AppMaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.uikit.theme.AppColors
import com.openapi.core.db.FavoriteVo
import com.openapi.feature.content.R

/**
 * 收藏夹（A18）：暖色卡片列表。点按打开，长按弹出「分享 / 取消收藏」。空状态有提示。
 */
@Composable
fun FavoriteScreen(
    items: List<FavoriteVo>,
    onOpen: (FavoriteVo) -> Unit,
    onShare: (FavoriteVo) -> Unit,
    onCancelFavorite: (FavoriteVo) -> Unit,
) {
    var actionTarget by remember { mutableStateOf<FavoriteVo?>(null) }

    if (items.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(AppColors.ChatBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.favorite_empty), color = AppColors.TextSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(AppColors.ChatBackground),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id ?: it.hashCode().toLong() }) { vo ->
                FavoriteCard(
                    vo = vo,
                    onClick = { onOpen(vo) },
                    onLongClick = { actionTarget = vo },
                )
            }
        }
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(target.name.orEmpty(), maxLines = 2) },
            text = null,
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    onCancelFavorite(target)
                }) { Text(stringResource(R.string.menu_favorite_cancel), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionTarget = null
                    onShare(target)
                }) { Text(stringResource(R.string.favorite_action_share), color = AppColors.Primary) }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(vo: FavoriteVo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                vo.name.orEmpty(),
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(typeLabel(vo.type), color = AppColors.Primary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(vo.updateTime.orEmpty(), color = AppColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun typeLabel(type: Int): String = when (type) {
    com.openapi.core.config.Constants.FavoriteType.TEXT -> stringResource(R.string.favorite_type_text)
    com.openapi.core.config.Constants.FavoriteType.SOUND -> stringResource(R.string.favorite_type_sound)
    com.openapi.core.config.Constants.FavoriteType.IMG -> stringResource(R.string.favorite_type_image)
    com.openapi.core.config.Constants.FavoriteType.VIDEO -> stringResource(R.string.favorite_type_video)
    else -> ""
}

/** 收藏列表的状态：Fragment 读取本地收藏后写入 [items]，界面自动刷新。 */
class FavoriteListState {
    var items: List<FavoriteVo> by mutableStateOf(emptyList())
        private set

    fun replace(list: List<FavoriteVo>) {
        items = list
    }
}

/** 给 Fragment 用：把 Compose 列表设为 [view] 的内容（Java 不能直接写 Compose 内容）。 */
fun ComposeView.setFavoriteContent(
    state: FavoriteListState,
    onOpen: (FavoriteVo) -> Unit,
    onShare: (FavoriteVo) -> Unit,
    onCancelFavorite: (FavoriteVo) -> Unit,
) {
    setContent {
        AppMaterialTheme {
            FavoriteScreen(state.items, onOpen, onShare, onCancelFavorite)
        }
    }
}
