package com.openapi.ks.myapp.dh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 长按她之后弹出的动作托盘：盖在舞台上的一层薄纱 + 底部一张小卡。
 *
 * - 可点动作：发一条聊天通道消息（内容由 [AvatarAction.messageText] 定），发完收起。
 * - 置灰动作：行尾直接写出原因（如「等模型动作素材」），点了不响应——
 *   原因要看得见，不能是个点了没反应的哑按钮。
 * - 薄纱任意处点击 = 收起。不用 Dialog：托盘贴着人物，挂断时随舞台一起消失。
 */
@Composable
fun AvatarActionTray(
    actions: List<AvatarAction>,
    onActionClick: (AvatarAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCREAM)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 132.dp) // 让开底部通话控件条
                .background(CARD_BG, shape)
                .border(1.dp, CARD_BORDER, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("想对她做点什么？", color = TEXT_PRIMARY, fontSize = 13.sp)
            actions.forEach { action ->
                val label = if (action.enabled) action.label else "${action.label} · ${action.disabledReason}"
                Text(
                    text = label,
                    color = if (action.enabled) TEXT_PRIMARY else TEXT_DISABLED,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .semantics { contentDescription = label }
                        .clickable(enabled = action.enabled) { onActionClick(action) }
                        .fillMaxWidth()
                        .padding(vertical = 7.dp, horizontal = 6.dp),
                )
            }
        }
    }
}

private val SCREAM = Color(0x66000000)
private val CARD_BG = Color(0xF2101731)
private val CARD_BORDER = Color(0x33FFFFFF)
private val TEXT_PRIMARY = Color(0xFFF3F5FF)
private val TEXT_DISABLED = Color(0xFF6B7591)
