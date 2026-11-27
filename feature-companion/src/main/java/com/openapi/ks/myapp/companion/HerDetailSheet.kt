package com.openapi.ks.myapp.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * her 详情抽屉（稿 02「她长什么样」的第二层）：点列表卡打开，画这个 her 的
 * **形象 / 人格 / 介绍 / 装饰** 与 her 包的下载安装状态。
 *
 * 叠加层纪律（不改原 UI）：列表页其余部分原样，抽屉只做展示与转发动作——
 * - 「使用这个形象」走的仍是列表页那条 [HerListViewModel.pick]（写 [com.openapi.ks.myapp.dh.StageChoice]，
 *   通话页 resolveCallStage 仍是唯一决定点），草稿卡因此照样不可用；
 * - 下载按钮的状态全部来自 [HerPackState]（HerPackSync 的五态），文案与可点性由
 *   [herDownloadButtonLabel] / [herDownloadButtonEnabled] 现算，UI 不存第二份状态；
 * - 详情没取到（老服务端 / 404 / 断网）就明说取不到，不画假立绘、不编装饰
 *   （与列表页「没素材不假装有图」同一口径）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerDetailSheet(
    state: HerDetailState,
    onUse: (HerSummary) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val summary = state.summary
    val detail = state.detail
    val selectable = herSelectability(summary) == HerSelectability.SELECTABLE
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                herDisplayName(summary),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HerPill(herRendererLabel(summary.renderer))
                HerPill(herStatusLabel(summary.status), accent = summary.status == "published")
                herVersionLabel(if (detail != null && detail.packVersion > 0) detail.asSummary() else summary)?.let { HerPill(it) }
            }

            when {
                state.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("正在读取她的详情…", style = MaterialTheme.typography.bodySmall)
                }
                detail == null -> Text(
                    text = "这份详情还取不到（服务端可能还没上 her 详情）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    // 形象：立绘只在服务端真给了签名 URL 时画；没给不画占位图。
                    if (detail.portraitUrl.isNotBlank()) {
                        AsyncImage(
                            model = detail.portraitUrl,
                            contentDescription = "${herDisplayName(summary)} 的立绘",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    // 介绍
                    if (detail.tagline.isNotBlank()) {
                        Text(detail.tagline, style = MaterialTheme.typography.bodyMedium)
                    }
                    // 人格：只读说明——换 her 不换人格，这里一个字都不写回 persona。
                    detail.persona?.let { persona ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "人格 · ${persona.name.trim().ifBlank { persona.roleId.trim() }}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (persona.description.isNotBlank()) {
                                Text(persona.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (persona.personality.isNotBlank()) {
                                Text(
                                    "性格：${persona.personality}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // 装饰：计数行 + 名字清单（服务端没给就不画，不编）。
                    herDecorationsLine(detail.effects, detail.stickers)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    herDecorationNames(detail).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // her 包下载安装：状态五态见 HerPackState；download_url 空 → 「暂不可下载」。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val downloadable = state.downloadable
                Button(
                    onClick = onDownload,
                    enabled = herDownloadButtonEnabled(state.packState, downloadable),
                ) {
                    Text(herDownloadButtonLabel(state.packState, downloadable))
                }
                detail?.let { herPackSizeLabel(it.model.sizeBytes)?.let { label -> Text(label, style = MaterialTheme.typography.bodySmall) } }
            }
            (state.packState as? HerPackState.VerifyFailed)?.let {
                Text(
                    "${it.message}（已安装的形象不受影响，可重试）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // 选用这个形象 = 列表页那条 pick（草稿 / 未接入的 renderer 照旧不兑现）。
            if (selectable) {
                Button(onClick = { onUse(summary) }) { Text("使用这个形象") }
            } else {
                herLockedReason(summary)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
