package com.openapi.chatkit.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
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

/** 长按菜单的样式（原型对比用）：横排卡片（Popup）或底部弹层（ModalBottomSheet，一行一项）。 */
enum class MessageMenuStyle { Popup, Sheet }

/** 长按菜单的一项：图标 + 文字，点击后执行 [action]。 */
internal data class MenuItem(val label: String, val action: MessageAction, val icon: ImageVector)

private val MenuCorner = 14.dp
private val MenuItemMinWidth = 64.dp
private const val MenuGap = 8

/**
 * 长按菜单：白色圆角卡片（轻阴影），一排图标 + 文字，按内容决定宽度，不画分割线。
 * 锚在 [anchor]（消息行在窗口里的位置）上方；上方放不下时放到下方。
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
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .widthIn(min = MenuItemMinWidth)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(item.action) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = MenuIconInk, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.label, fontSize = 12.sp, color = MenuTextInk, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val MenuIconInk = Color(0xFF1F1F1F)
private val MenuTextInk = Color(0xFF3A3A3A)

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

/** 底部弹层样式：每个动作一行（图标 + 文字），用 Material3 的 ModalBottomSheet，不引入新依赖。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageMenuSheet(
    items: List<MenuItem>,
    onSelect: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item.action) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, contentDescription = null, tint = MenuIconInk, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(item.label, fontSize = 16.sp, color = MenuTextInk)
                }
            }
        }
    }
}
