package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 整改清单 P2-15 会话搜索：会话列表整表已在 ChatUiState.sessions 里，
 * 过滤是纯内存操作，钉住三件事：大小写不敏感、空白词原样返回、未命名会话可预期。
 */
class ChatSearchTest {

    private fun session(id: Long, name: String) = ChatSession(id = id, name = name, isDefault = false)

    @Test
    fun filterSessionsIsCaseInsensitiveSubstring() {
        val sessions = listOf(session(1, "Spring Boot 笔记"), session(2, "旅行计划"))

        val hits = filterSessions(sessions, "boot")
        assertEquals(listOf(1L), hits.map { it.id })
    }

    @Test
    fun blankQueryKeepsTheWholeList() {
        val sessions = listOf(session(1, "A"), session(2, "B"))

        assertEquals(sessions, filterSessions(sessions, ""))
        assertEquals(sessions, filterSessions(sessions, "   "))
    }

    @Test
    fun filterSessionsPreservesInputOrder() {
        val sessions = listOf(session(1, "周会"), session(2, "周报"), session(3, "周末周游"))

        assertEquals(listOf(1L, 2L, 3L), filterSessions(sessions, "周").map { it.id })
    }
}
