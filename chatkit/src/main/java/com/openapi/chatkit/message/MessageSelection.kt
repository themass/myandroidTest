package com.openapi.chatkit.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.openapi.chatkit.R
import com.openapi.chatkit.theme.ChatKit

/**
 * 多选状态：长按消息菜单里的「多选」进入；再点消息切换选中。
 * 只记录消息 id，不关心消息内容；删除等动作由调用方决定。
 */
@Stable
class MessageSelectionState {
    private val ids = mutableStateListOf<String>()

    var active by mutableStateOf(false)
        private set

    val selectedIds: List<String> get() = ids.toList()

    val count: Int get() = ids.size

    fun isSelected(id: String): Boolean = id in ids

    /** 进入多选，并选中 [firstId]。 */
    fun start(firstId: String) {
        active = true
        if (firstId !in ids) ids.add(firstId)
    }

    /** 多选中切换一条消息的选中状态；非多选状态下无效。 */
    fun toggle(id: String) {
        if (!active) return
        if (id in ids) ids.remove(id) else ids.add(id)
    }

    /** 退出多选并清空选择。 */
    fun cancel() {
        active = false
        ids.clear()
    }
}

@Composable
fun rememberMessageSelection(): MessageSelectionState = remember { MessageSelectionState() }

/** 多选时替代输入栏的底部操作条：左「取消」，中间已选数量，右「删除」（没选时禁用）。 */
@Composable
fun MessageSelectionBar(
    count: Int,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.chatkit_selection_cancel), color = colors.secondaryText, fontSize = 16.sp)
        }
        Text(
            stringResource(R.string.chatkit_selected_count, count),
            color = colors.bubbleText,
            fontSize = 15.sp,
        )
        TextButton(onClick = onDelete, enabled = count > 0) {
            Text(
                stringResource(R.string.chatkit_delete),
                color = if (count > 0) colors.error else colors.secondaryText,
                fontSize = 16.sp,
            )
        }
    }
}

/** 多选时消息左侧的圆形勾选标记：未选是空心圆环，选中是实心主色圆 + 对勾。 */
@Composable
internal fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = ChatKit.colors
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .size(22.dp)
            .then(
                if (selected) Modifier.background(colors.accent, CircleShape)
                else Modifier.border(1.5.dp, colors.secondaryText, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
    }
}
