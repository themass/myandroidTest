package com.openapi.chatkit.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chatkit.R

/*
 * 转文字编辑态（SPEC2 第 6 项）：识别出的文字可以改；下面三个按钮：
 * 取消（圆形 ×）、发送原语音（圆形）、发送（胶囊）。
 * 颜色沿用微信的绿色气泡和浅灰按钮；位置在输入卡片上方（不是全屏浮层）。
 */

private val DraftBubble = Color(0xFF95EC69)
private val DraftButton = Color(0xFFEEEEEE)
private val DraftSendCapsule = Color(0xFFDDDDDD)

/** 转文字编辑面板：可编辑的识别文字 + 三个操作。 */
@Composable
internal fun VoiceDraftEditor(
    initialText: String,
    onSendText: (String) -> Unit,
    onSendAudio: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DraftBubble)
                .padding(12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(fontSize = 17.sp, color = Color.Black),
                cursorBrush = SolidColor(Color.Black),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DraftAction(
                label = stringResource(R.string.chatkit_cancel_short),
                onClick = onCancel,
                icon = { Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp)) },
            )
            DraftAction(
                label = stringResource(R.string.chatkit_send_original_voice),
                onClick = onSendAudio,
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp)) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 128.dp, height = 68.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(DraftSendCapsule)
                        .clickable { onSendText(text) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.chatkit_send), fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun DraftAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DraftButton)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFF666666))
    }
}
