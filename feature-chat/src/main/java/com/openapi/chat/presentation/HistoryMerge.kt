package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.DeliveryStatus

/**
 * 重新加载服务端历史时，和当前列表合并。从 ChatViewModel.fallbackReloadMessages 拆出来，便于测试。
 *
 * 规则：
 * 1. 服务端历史里没有「失败」状态：本地标成失败的用户消息，如果服务端有文字相同、时间相差不超过 2 分钟的
 *    同一条，就把它标为失败（不能只保留最后一条）。
 * 2. 本地还没拿到服务端 id 的用户消息（id 以 local- 开头）保留下来，不被历史覆盖掉；已经和服务端对上的不重复保留。
 * 3. 合并后的列表按创建时间排序，本地消息插入正确位置，不追加在末尾。
 */
internal object HistoryMerge {

    private const val MATCH_WINDOW_MS = 120_000L

    fun merge(current: List<ChatMessage>, server: List<ChatMessage>): List<ChatMessage> {
        val failedLocal = current.filter { it.role == ChatRole.USER && it.delivery == DeliveryStatus.FAILED }
        val merged = server.map { m ->
            val hit = m.role == ChatRole.USER && failedLocal.any { f -> matches(f, m) }
            if (hit) m.copy(delivery = DeliveryStatus.FAILED) else m
        }
        val serverIds = server.map { it.id }.toSet()
        val matchedLocalIds = server.filter { it.role == ChatRole.USER }.flatMap { m ->
            failedLocal.filter { f -> matches(f, m) }.map { it.id }
        }.toSet()
        val localOnly = current.filter {
            it.role == ChatRole.USER && it.id.startsWith("local-") && it.id !in serverIds && it.id !in matchedLocalIds
        }
        return (merged + localOnly).sortedBy { it.createdAtMillis }
    }

    private fun matches(local: ChatMessage, server: ChatMessage): Boolean =
        local.text == server.text && kotlin.math.abs(local.createdAtMillis - server.createdAtMillis) <= MATCH_WINDOW_MS
}
