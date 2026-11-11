package com.openapi.ks.myapp.agent.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openapi.ks.myapp.agent.AgentApi
import kotlinx.coroutines.launch

/** 新对话页的入口可选角色。id 与服务端内置陪伴角色一致（BUILTIN_PERSONA_IDS）。 */
data class AgentRoleChoice(val id: String, val name: String, val tagline: String)

val DefaultAgentRoles = listOf(
    AgentRoleChoice("linche", "林澈", "温和、会记事的倾听者"),
    AgentRoleChoice("sudai", "苏黛", "直率、爱开玩笑的朋友"),
)

/**
 * 每个角色记住最近一次的会话 id：再次进入时继续那段对话（服务端按会话保存上下文与记忆）。
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
 * 抽屉里「角色对话（新）」的页面：先选角色，再进入该角色的会话（没有就创建）。
 * 顶栏可以返回角色列表，或为当前角色开一段新对话。
 */
@Composable
fun AgentChatEntry(
    modifier: Modifier = Modifier,
    roles: List<AgentRoleChoice> = DefaultAgentRoles,
) {
    val context = LocalContext.current.applicationContext
    val api: AgentApi = remember(context) { buildAgentApi(context) }
    val sessions = remember(context) { AgentSessionMemory(context) }
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var roleId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun open(role: AgentRoleChoice, fresh: Boolean) {
        if (creating) return
        error = null
        roleId = role.id
        val known = if (fresh) null else sessions.get(role.id)
        if (known != null) {
            sessionId = known
            return
        }
        creating = true
        scope.launch {
            runCatching { api.createSession(roleId = role.id, locale = "zh-CN", name = role.name) }
                .onSuccess { id ->
                    sessions.put(role.id, id)
                    sessionId = id
                }
                .onFailure {
                    error = "创建会话失败，请稍后再试"
                    roleId = null
                }
            creating = false
        }
    }

    val role = roles.firstOrNull { it.id == roleId }
    val current = sessionId
    if (role != null && current != null) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { sessionId = null; roleId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回角色列表")
                }
                Text(role.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { sessions.forget(role.id); sessionId = null; open(role, fresh = true) }) {
                    Text("新对话")
                }
            }
            // 会话 id 变了就换一个 ViewModel（AgentChatRoute 按会话 id 区分）
            AgentChatRoute(sessionId = current, isRtl = isRtl, modifier = Modifier.weight(1f))
        }
        return
    }

    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("选择角色", style = MaterialTheme.typography.titleLarge)
        Text(
            "每个角色的记忆互相独立。再次进入会继续上次的对话。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        roles.forEach { choice ->
            Card(Modifier.fillMaxWidth().clickable(enabled = !creating) { open(choice, fresh = false) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(choice.name, style = MaterialTheme.typography.titleMedium)
                    Text(choice.tagline, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (creating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text("正在创建会话…")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
    }
}
