package com.openapi.feature.user.setting

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.storage.StorageCatalog
import com.openapi.common.ui.theme.AppColors
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.res.stringResource
import com.openapi.common.ui.theme.AppSpacing
import com.openapi.feature.user.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 存储管理页的内容（外壳仍是 [com.openapi.legacyui.app.BaseSingleActivity]，与设置页一致）。
 *
 * 展示的是 [StorageCatalog] 里**登记过**的目录：名字（用户看得懂）、用途、体积、能否删除。
 * 没有登记的目录一律不显示——`filesDir` 里有第三方库的缓存，列出来让用户删是危险的。
 *
 * 两类目录的交互不同：
 * - **缓存**（`userData = false`）：直接清，弹一个轻提示；
 * - **用户数据**（`userData = true`，如解题历史）：必须确认，文案说明删了不可恢复。
 */
data class StorageUiState(
    val usages: List<StorageCatalog.Usage> = emptyList(),
    val loading: Boolean = true,
) {
    val totalBytes: Long get() = usages.sumOf { it.sizeBytes }
    val userDataBytes: Long get() = usages.filter { it.entry.userData }.sumOf { it.sizeBytes }
    val cacheBytes: Long get() = totalBytes - userDataBytes
}

@Composable
fun StorageScreen(
    onCleared: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StorageUiState()) }
    var confirming by remember { mutableStateOf<StorageCatalog.Usage?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }
    val scope = remember { Executors.newSingleThreadExecutor() }

    fun refresh() {
        state = state.copy(loading = true)
        scope.execute {
            val usages = runCatching { StorageCatalog.usage(context) }.getOrDefault(emptyList())
            // 结果回到主线程：Compose 状态只能在主线程改
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                state = StorageUiState(usages, loading = false)
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = AppColors.Primary)
                Spacer(Modifier.width(AppSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.storage_total, StorageCatalog.formatSize(state.totalBytes)),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        stringResource(
                            R.string.storage_breakdown,
                            StorageCatalog.formatSize(state.cacheBytes),
                            StorageCatalog.formatSize(state.userDataBytes),
                        ),
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                    )
                }
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                } else {
                    TextButton(onClick = { refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                stringResource(R.string.storage_scope_hint),
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
        }

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.storage_clear_all_caches),
                    fontSize = 15.sp,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { confirmingAll = true },
                    enabled = !state.loading && state.cacheBytes > 0,
                ) { Text(stringResource(R.string.storage_clear)) }
            }
            Text(
                stringResource(R.string.storage_clear_all_hint),
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
        }

        // 用户数据在前（更重要），缓存在后
        state.usages.sortedByDescending { it.entry.userData }.forEach { usage ->
            StorageEntryCard(
                usage = usage,
                onClear = {
                    if (usage.entry.userData) confirming = usage
                    else {
                        clearAsync(scope, context, listOf(usage.entry)) { refresh(); onCleared(usage.entry.title) }
                    }
                },
            )
        }

        Spacer(Modifier.height(AppSpacing.xl))
    }

    confirming?.let { usage ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.storage_delete_title, usage.entry.title)) },
            text = { Text(stringResource(R.string.storage_delete_body, usage.entry.description)) },
            confirmButton = {
                TextButton(onClick = {
                    val entry = usage.entry
                    confirming = null
                    clearAsync(scope, context, listOf(entry)) { refresh(); onCleared(entry.title) }
                }) { Text(stringResource(R.string.storage_delete), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.storage_cancel)) }
            },
        )
    }

    if (confirmingAll) {
        AlertDialog(
            onDismissRequest = { confirmingAll = false },
            title = { Text(stringResource(R.string.storage_clear_all_title)) },
            text = { Text(stringResource(R.string.storage_clear_all_body, StorageCatalog.formatSize(state.cacheBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingAll = false
                    val caches = state.usages.map { it.entry }.filterNot { it.userData }
                    clearAsync(scope, context, caches) { refresh(); onCleared("caches") }
                }) { Text(stringResource(R.string.storage_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingAll = false }) { Text(stringResource(R.string.storage_cancel)) }
            },
        )
    }
}

/** 后台清理若干目录，完成后回主线程回调。 */
private fun clearAsync(
    executor: ExecutorService,
    context: Context,
    entries: List<StorageCatalog.Entry>,
    onDone: () -> Unit,
) {
    executor.execute {
        entries.forEach { runCatching { StorageCatalog.clear(context, it) } }
        android.os.Handler(android.os.Looper.getMainLooper()).post(onDone)
    }
}

@Composable
private fun StorageEntryCard(usage: StorageCatalog.Usage, onClear: () -> Unit) {
    val entry = usage.entry
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary,
                    )
                    if (entry.userData) {
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(
                            stringResource(R.string.storage_user_data_tag),
                            fontSize = 11.sp,
                            color = AppColors.Warning,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.Warning.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (usage.missing) {
                        stringResource(R.string.storage_empty)
                    } else {
                        stringResource(
                            R.string.storage_usage,
                            StorageCatalog.formatSize(usage.sizeBytes),
                            usage.fileCount,
                        )
                    },
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                )
            }
            OutlinedButton(onClick = onClear, enabled = !usage.missing && usage.sizeBytes > 0) {
                Text(stringResource(if (entry.userData) R.string.storage_delete else R.string.storage_clear))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.description, fontSize = 12.sp, color = AppColors.TextSecondary)
        // 路径对普通用户没用，但对排查问题有用；放在最后一行小字里
        Text(
            usage.path,
            fontSize = 10.sp,
            color = AppColors.TextSecondary.copy(alpha = 0.7f),
        )
    }
}
