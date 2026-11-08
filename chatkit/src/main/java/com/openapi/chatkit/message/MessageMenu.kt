package com.openapi.chatkit.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.openapi.chatkit.model.MessageAction
import kotlin.math.roundToInt

/** 长按菜单的一项：纯文字，点击后执行 [action]。 */
internal data class MenuItem(val label: String, val action: MessageAction)

/** 豆包式长按菜单（照抄 `larus_layout_message_menu`）：白底、圆角 12dp、横排等宽的文字项，项之间一条细分割线。 */
/** 每项约 88dp 宽，整体随项数变化（2–4 项），不再固定 300dp。 */
private const val MenuItemWidthDp = 88
private val MenuCorner = 12.dp
private val MenuDivider = Color(0xFFEFEFEF)
private val MenuGap = 8

/**
 * 长按菜单。锚在 [anchor]（消息行在窗口里的位置）上方；上方放不下时放到下方。
 * 点菜单项 → [onSelect]；点菜单外面 → [onDismiss]。
 */
@Composable
internal fun MessageMenu(
    anchor: Rect,
    items: List<MenuItem>,
    onSelect: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = MenuPosition(anchor),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(MenuCorner),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.width((MenuItemWidthDp * items.size.coerceAtLeast(1)).dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(MenuDivider))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(item.action) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.label, fontSize = 15.sp, color = Color.Black, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 菜单位置：水平居中对准消息，竖直放在消息上方；上方放不下就放到下方。窗口边缘内收。 */
private class MenuPosition(private val anchor: Rect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchor.center.x - popupContentSize.width / 2f).roundToInt().coerceIn(0, maxX)
        val above = anchor.top.roundToInt() - popupContentSize.height - MenuGap
        val y = if (above >= 0) above else anchor.bottom.roundToInt() + MenuGap
        return IntOffset(x, y)
    }
}
