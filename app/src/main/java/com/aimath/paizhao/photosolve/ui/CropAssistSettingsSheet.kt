package com.aimath.paizhao.photosolve.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimath.paizhao.photosolve.cropassist.CropAssistProvider
import com.aimath.paizhao.photosolve.cropassist.CropAssistRegistry

/** Lets the user pick which [CropAssistProvider] auto-suggests the crop box on the next capture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropAssistSettingsSheet(
    registry: CropAssistRegistry,
    activeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("自动识别方案", style = MaterialTheme.typography.titleLarge)
            Text(
                "拍照后进入裁剪页时,自动用所选方案给出建议框;仍可手动调整",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            registry.all.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider.id == activeId,
                    onSelect = { onSelect(provider.id) },
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: CropAssistProvider,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var available by remember(provider.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(provider.id) { available = provider.isAvailable() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (available) {
                    true -> "可用"
                    false -> "不可用"
                    null -> "检测中…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
