package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMergeTest {

    private fun user(id: String, text: String, at: Long, delivery: DeliveryStatus = DeliveryStatus.SENT) =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = at, delivery = delivery)

    private fun assistant(id: String, text: String, at: Long) =
        ChatMessage(id = id, role = ChatRole.ASSISTANT, text = text, createdAtMillis = at)

    @Test
    fun failedMarkSurvivesReloadWhenTextAndTimeMatch() {
        val current = listOf(user("local-1", "你好", 1_000L, DeliveryStatus.FAILED))
        val server = listOf(user("srv-1", "你好", 1_500L))
        val merged = HistoryMerge.merge(current, server)
        assertEquals(1, merged.size)
        assertEquals(DeliveryStatus.FAILED, merged[0].delivery)
    }

    @Test
    fun everyFailedMessageKeepsItsMarkNotOnlyTheLast() {
        // 两条都失败：前一条不能因为后面又来了一条失败而丢掉失败标记
        val current = listOf(
            user("local-1", "第一条", 1_000L, DeliveryStatus.FAILED),
            user("local-2", "第二条", 5_000L, DeliveryStatus.FAILED),
        )
        val server = listOf(user("srv-1", "第一条", 1_000L), user("srv-2", "第二条", 5_000L))
        val merged = HistoryMerge.merge(current, server)
        assertEquals(listOf(DeliveryStatus.FAILED, DeliveryStatus.FAILED), merged.map { it.delivery })
    }

    @Test
    fun unsyncedLocalMessageIsKeptAndPlacedByTime() {
        // 图片还在发送、没有服务端 id，历史里有更晚的消息：本地消息必须排在它应有的位置，不能追加到末尾
        val current = listOf(user("local-img", "[图片]", 1_000L))
        val server = listOf(assistant("srv-a", "回复", 2_000L), user("srv-u", "后来的文字", 3_000L))
        val merged = HistoryMerge.merge(current, server)
        assertEquals(listOf("local-img", "srv-a", "srv-u"), merged.map { it.id })
    }

    @Test
    fun localMessageAlreadyMatchedOnServerIsNotDuplicated() {
        val current = listOf(user("local-1", "你好", 1_000L, DeliveryStatus.FAILED))
        val server = listOf(user("srv-1", "你好", 1_000L))
        val merged = HistoryMerge.merge(current, server)
        assertEquals(listOf("srv-1"), merged.map { it.id })
        assertTrue(merged.none { it.id == "local-1" })
    }

    @Test
    fun matchOutsideTwoMinutesDoesNotMark() {
        val current = listOf(user("local-1", "你好", 0L, DeliveryStatus.FAILED))
        val server = listOf(user("srv-1", "你好", 200_000L))
        val merged = HistoryMerge.merge(current, server)
        // 服务端那条不标失败；本地那条没有对上，仍然保留并标失败
        assertEquals(DeliveryStatus.SENT, merged.first { it.id == "srv-1" }.delivery)
        assertEquals(DeliveryStatus.FAILED, merged.first { it.id == "local-1" }.delivery)
    }
}
