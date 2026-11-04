package com.openapi.myapp.ui.proton

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.openapi.common.util.EventBusUtil
import com.openapi.data.local.UserNodeStore
import com.openapi.data.remote.ShareLinkParser
import com.openapi.myapp.data.config.LocationChooseEvent
import com.openapi.ks.free1.R

/**
 * Private 页的数据层：读写本机的用户节点，并把选中结果告诉首页。
 * 新旧两套节点列表都通过它承载 UserNodesPanel，行为完全一致。
 */
@Composable
internal fun PrivateNodesHost(onChosen: () -> Unit) {
    val context = LocalContext.current
    var nodes by remember { mutableStateOf(UserNodeStore.list(context)) }
    var selectedId by remember { mutableStateOf(UserNodeStore.selected(context)?.id) }

    fun reload() {
        nodes = UserNodeStore.list(context)
        selectedId = UserNodeStore.selected(context)?.id
    }

    UserNodesPanel(
        nodes = nodes,
        selectedUserId = selectedId,
        onSelect = { id ->
            // 选中用户节点：首页改用它；服务器节点的选择保留，但首页会优先用用户节点
            UserNodeStore.select(context, id)
            EventBusUtil.getEventBus().postSticky(LocationChooseEvent())
            onChosen()
            reload()
        },
        onSave = { id, name, link -> save(context, id, name, link).also { reload() } },
        onDelete = { id ->
            UserNodeStore.remove(context, id)
            reload()
        },
    )
}

/** 保存用户节点：null 表示成功，否则返回错误文字。 */
private fun save(context: Context, id: String?, name: String, link: String): String? {
    if (link.isBlank()) return context.getString(R.string.user_node_link_empty)
    return try {
        if (id == null) {
            UserNodeStore.addFromLink(context, link)
        } else {
            UserNodeStore.update(context, id, name, link)
        }
        null
    } catch (e: ShareLinkParser.ParseException) {
        context.getString(R.string.user_node_invalid, e.message ?: "")
    }
}
