package com.openapi.ks.myapp.dh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OriginalAvatarDemoControls(
    smileEnabled: Boolean,
    capeEnabled: Boolean,
    onSmileChange: (Boolean) -> Unit,
    onCapeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "原创 Live2D Demo",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = smileEnabled,
                    onClick = { onSmileChange(!smileEnabled) },
                    label = { Text("笑脸") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = capeEnabled,
                    onClick = { onCapeChange(!capeEnabled) },
                    label = { Text("披风") },
                )
            }
        }
    }
}
