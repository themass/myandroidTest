package com.openapi.ks.myapp.agent.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openapi.core.CommonConstants
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.ks.myapp.agent.AgentApi
import com.openapi.ks.myapp.agent.AgentChatRepository
import com.openapi.ks.myapp.agent.AgentChatRepositoryImpl
import com.openapi.ks.myapp.agent.AgentEventReconnector
import com.openapi.ks.myapp.agent.AgentEventSource
import com.openapi.ks.myapp.agent.HttpAgentApi
import com.openapi.ks.myapp.agent.cache.AgentMessageCache
import com.openapi.ks.myapp.companion.CompanionConfig

/**
 * 新对话页的装配：基地址与登录 token 来自现有配置（与陪伴页一致），不关心 HTTP 细节。
 * 会话 id 由调用方给出（先 POST /sessions 创建，见 [AgentApi.createSession]）。
 */
@Composable
fun AgentChatRoute(
    sessionId: String,
    isRtl: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { buildRepository(context) }
    val viewModel: AgentChatViewModel = viewModel(
        key = "agent-chat-$sessionId",
        factory = viewModelFactory {
            initializer {
                AgentChatViewModel(repository, sessionId, AgentMessageCache(context))
            }
        },
    )
    AgentChatScreen(viewModel = viewModel, isRtl = isRtl, modifier = modifier)
}

internal fun buildAgentApi(context: Context): AgentApi =
    HttpAgentApi(baseUrl = CompanionConfig.baseUrl, tokenProvider = tokenOf(context),
        client = AgentEventSource.defaultClient())

private fun tokenOf(context: Context): () -> String? =
    { PreferenceUtils.getPrefString(context, CommonConstants.HTTP_TOKEN_KEY, null) }

private fun buildRepository(context: Context): AgentChatRepository {
    val tokenProvider = tokenOf(context)
    val baseUrl = CompanionConfig.baseUrl
    val api: AgentApi = buildAgentApi(context)
    val source = AgentEventSource(baseUrl = baseUrl, tokenProvider = tokenProvider,
        client = AgentEventSource.defaultClient())
    return AgentChatRepositoryImpl(api, AgentEventReconnector(source))
}
