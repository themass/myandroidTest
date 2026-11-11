package com.openapi.ks.myapp.agent.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 每个角色记住最近一次的会话 id：再次进入时继续那段对话（服务端按会话保存上下文，记忆按角色分空间）。
 * 只存 id，不存内容。
 */
class AgentSessionMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("agent_chat_sessions", Context.MODE_PRIVATE)

    fun get(roleId: String): String? = prefs.getString(roleId, null)

    fun put(roleId: String, sessionId: String) {
        prefs.edit().putString(roleId, sessionId).apply()
    }

    fun forget(roleId: String) {
        prefs.edit().remove(roleId).apply()
    }
}

/**
 * 「陪伴」页里的文字对话：当前角色的会话（没有就创建），换角色自动换会话。
 *
 * 角色选择、记忆、事件、设置都在陪伴页自己的入口里，这里只管对话本身。
 * [onAvatarState] 把「正在想 / 正在说 / 空闲」报给上面的形象。
 */
@Composable
fun AgentRoleChat(
    roleId: String,
    onAvatarState: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val api = remember(context) { buildAgentApi(context) }
    val sessions = remember(context) { AgentSessionMemory(context) }
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var sessionId by remember(roleId) { mutableStateOf(sessions.get(roleId)) }
    var failed by remember(roleId) { mutableStateOf(false) }
    // 换角色或点「新对话」时递增，触发重新创建
    var attempt by remember(roleId) { mutableIntStateOf(0) }

    LaunchedEffect(roleId, attempt) {
        if (sessionId != null) return@LaunchedEffect
        failed = false
        runCatching { api.createSession(roleId = roleId, locale = "zh-CN", name = roleId) }
            .onSuccess { id ->
                sessions.put(roleId, id)
                sessionId = id
            }
            .onFailure { failed = true }
    }

    val current = sessionId
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("", Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    sessions.forget(roleId)
                    sessionId = null
                    attempt++
                }
            }) { Text("新对话") }
        }
        when {
            current != null -> AgentChatRoute(
                sessionId = current, isRtl = isRtl, modifier = Modifier.weight(1f),
                onAvatarState = onAvatarState,
            )
            failed -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("创建会话失败，请检查网络后重试", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("重试") }
            }
            else -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
        }
    }
}
