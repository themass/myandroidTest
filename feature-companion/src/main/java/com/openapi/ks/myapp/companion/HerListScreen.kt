package com.openapi.ks.myapp.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openapi.ks.myapp.dh.StageChoice

/**
 * her 形象列表页（OpenDesign 稿 02「她长什么样」）。
 *
 * 稿子的核心语义在这里落成两条硬规则：
 * - **换 her 不换人格**：页顶明说「人格保持不变」，卡片上只有呈现相关的东西
 *   （renderer / 发布状态 / 版本 / 特效与贴纸数量），没有任何人格字段；
 * - **选中即换**：点一张可选的卡 = 写一条 [StageChoice]，下一次进通话页
 *   [com.openapi.ks.myapp.dh.resolveCallStage] 就用新引擎。页面不持有选择状态。
 *
 * 深色稿里的缩略图是渲染出来的形象；这一版**不放占位图**——没有 her 包真素材时，
 * 一张假剪影比没有图更误导（与通话页「素材没装时不假装有图」同一口径）。
 */
@Composable
fun HerListScreen(
    state: HerListState,
    onPick: (HerSummary) -> Unit,
    onFollowDefault: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        HerListHeader(roleName = state.roleName, onBack = onBack)

        if (state.choice != StageChoice.Unset) {
            // 用户自己定过舞台：给一条明确的反悔入口，否则「跟随服务端」再也回不去。
            OutlinedButton(onClick = onFollowDefault, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("改回跟随她的默认形象")
            }
        }

        when {
            state.loading && state.items.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            state.registryEmpty -> {
                Text(
                    text = "她还没有可选的形象。服务端上架 her 包之后，这里会列出来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.id }) { her ->
                    val selection = state.selection
                    val selected = selection.selectedHerId == her.id
                    HerCard(
                        her = her,
                        selected = selected,
                        followingDefault = selected && selection.followingDefault,
                        onClick = { onPick(her) },
                    )
                }
            }
        }
    }
}

/** 卡片：缩略位（无素材就不画）+ 名称 + renderer/状态/版本 + 特效贴纸数量。 */
@Composable
private fun HerCard(
    her: HerSummary,
    selected: Boolean,
    followingDefault: Boolean,
    onClick: () -> Unit,
) {
    val selectable = herSelectability(her) == HerSelectability.SELECTABLE
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .then(
                if (selected) Modifier.border(2.dp, HerViolet, shape) else Modifier,
            )
            .then(if (selectable) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态锚点：选中打勾，不可选画一把锁的语义（文字说明在行内）。
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (selected) HerViolet.copy(alpha = 0.22f) else HerViolet.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = HerViolet,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = herDisplayName(her).take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = HerViolet,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    herDisplayName(her),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        herSelectionBadge(followingDefault),
                        style = MaterialTheme.typography.labelSmall,
                        color = HerViolet,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (her.tagline.isNotBlank()) {
                Text(her.tagline, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HerPill(herRendererLabel(her.renderer))
                HerPill(herStatusLabel(her.status), accent = her.status == "published")
                herVersionLabel(her)?.let { HerPill(it) }
            }
            herMetaLine(her)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // 不可选的卡把原因写在脸上，而不是点下去没反应让用户猜。
            herLockedReason(her)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HerPill(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (accent) HerViolet else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (accent) HerViolet.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** 页顶：返回 + 「她长什么样」+「人格不变」的说明（稿 02 的核心承诺）。 */
@Composable
private fun HerListHeader(roleName: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("她长什么样", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = if (roleName.isNotBlank()) {
                "$roleName 的人格保持不变 · 选择她的呈现"
            } else {
                "人格保持不变 · 选择她的呈现"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewHers = listOf(
    HerSummary(
        id = "simple-linche-v1",
        name = "林澈 · 海边",
        tagline = "安静的默认形象",
        renderer = "simple",
        status = "published",
        packVersion = 3,
        effectCount = 4,
        stickerCount = 2,
    ),
    HerSummary(
        id = "live2d-linche-v2",
        name = "林澈 · Live2D",
        tagline = "会动的形象",
        renderer = "live2d",
        status = "published",
        packVersion = 2,
        effectCount = 5,
        stickerCount = 8,
    ),
    HerSummary(
        id = "live2d-linche-v3",
        name = "林澈 · Live2D 新装",
        tagline = "还在验收",
        renderer = "live2d",
        status = "draft",
        packVersion = 1,
    ),
)

@Preview(name = "her list", showBackground = true, widthDp = 390)
@Composable
private fun HerListPreview() {
    MaterialTheme {
        HerListScreen(
            state = HerListState(
                roleId = "linche",
                roleName = "林澈",
                items = hersForRole(previewHers, "linche"),
                choice = StageChoice.Engine(com.openapi.ks.myapp.dh.DhEngineId.LIVE2D),
            ),
            onPick = {},
            onFollowDefault = {},
            onBack = {},
        )
    }
}

@Preview(name = "her list empty", showBackground = true, widthDp = 390)
@Composable
private fun HerListEmptyPreview() {
    MaterialTheme {
        HerListScreen(
            state = HerListState(roleId = "linche", roleName = "林澈"),
            onPick = {},
            onFollowDefault = {},
            onBack = {},
        )
    }
}
