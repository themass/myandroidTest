package com.openapi.myapp.ui.proton

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.openapi.common.util.EventBusUtil
import com.openapi.data.local.UserNodeRepository
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

    val scope = rememberCoroutineScope()

    // 进入页面时从服务端同步一次（登录状态下），之后以服务端为准
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { UserNodeRepository.pull(context) }
        reload()
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
        // 写操作先调服务端，成功后才改本地；失败时把错误回传给对话框，列表保持不变
        onSave = { id, name, link, done ->
            scope.launch {
                val error = withContext(Dispatchers.IO) { saveRemote(context, id, name, link) }
                reload()
                done(error)
            }
        },
        onDelete = { id ->
            scope.launch {
                withContext(Dispatchers.IO) { UserNodeRepository.delete(context, id) }
                reload()
            }
        },
    )
}

/** 服务端保存：成功返回 null，失败返回错误文字。 */
private fun saveRemote(context: Context, id: String?, name: String, link: String): String? {
    if (link.isBlank()) return context.getString(R.string.user_node_link_empty)
    if (!UserNodeRepository.isLoggedIn()) return context.getString(R.string.user_node_need_login)
    return if (UserNodeRepository.save(context, id, name, link) != null) null
    else context.getString(R.string.user_node_save_failed)
}
