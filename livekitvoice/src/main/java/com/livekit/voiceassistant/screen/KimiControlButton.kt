package com.livekit.voiceassistant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Chat
import com.livekit.voiceassistant.R

@Composable
fun KimiControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = colorResource(id = R.color.kimi_button_primary)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(dimensionResource(id = R.dimen.kimi_control_button_size)),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(dimensionResource(id = R.dimen.kimi_control_button_icon_size)),
                tint = colorResource(id = R.color.kimi_text_primary)
            )
        }
        
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.kimi_padding_sm)))
        
        Text(
            text = label,
            fontSize = dimensionResource(id = R.dimen.kimi_text_size_sm).value.sp,
            color = colorResource(id = R.color.kimi_text_secondary)
        )
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "暂停按钮")
@Composable
fun KimiControlButtonPausePreview() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        KimiControlButton(
            icon = Icons.Default.PlayArrow,
            label = "暂停",
            onClick = { }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "聊天按钮")
@Composable
fun KimiControlButtonChatPreview() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E)),
        contentAlignment = Alignment.Center
    ) {
        KimiControlButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "聊天",
            onClick = { }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "按钮组合")
@Composable
fun KimiControlButtonGroupPreview() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFF1A0B2E))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        KimiControlButton(
            icon = Icons.Default.PlayArrow,
            label = "暂停",
            onClick = { }
        )
        KimiControlButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "聊天",
            onClick = { }
        )
    }
}
