package com.openapi.core.cast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.core.cast.DlnaDevice
import com.openapi.core.cast.DlnaDiscovery

/**
 * 投屏设备选择面板（Compose 版）。
 *
 * `:app` 里的 `CastDevicesDialog` 是 View 体系的（给 Jzvd 页面用），这里是 Compose 版，
 * 给本地视频这类 Compose 页面用。两者都走 [DlnaDiscovery]，协议逻辑只有一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePicker(
    onDismiss: () -> Unit,
    onSelected: (DlnaDevice) -> Unit,
) {
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchRound by remember { mutableStateOf(0) }

    // 直接在协程里 await 搜索结果，不用回调——少一层嵌套，也避开编���器在
    // SAM + composable lambda 组合上的后端崩溃
    LaunchedEffect(searchRound) {
        searching = true
        devices = runCatching { DlnaDiscovery().discover(4_000) }.getOrElse { emptyList() }
        searching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择投屏设备",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchRound += 1 }, enabled = !searching) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新搜索")
                }
            }

            DeviceList(
                devices = devices,
                searching = searching,
                onSelected = onSelected,
            )

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DlnaDevice>,
    searching: Boolean,
    onSelected: (DlnaDevice) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (searching) {
            CircularProgressIndicator()
        } else if (devices.isEmpty()) {
            Text(
                text = "没有找到可投屏设备，请确认电视与本机在同一 Wi-Fi",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn {
                items(devices) { device ->
                    DeviceRow(device = device, onClick = { onSelected(device) })
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DlnaDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(device.friendlyName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            device.modelName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
