package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * 「已经从界面上删掉、服务端那边还没确认」的墓碑集合。
 *
 * 为什么必须有：删除改成乐观之后（[MessageRemoval]），界面**先**空出来，服务端删除在后台补。
 * 这期间任何一次「用服务端历史重建列表」的路径——兜底对齐（fallbackReloadMessages）、
 * 加载更早的一页（[HistoryActions.loadOlder]）、切会话/刷新（[ChatViewModel.refresh]）——
 * 都会把刚删掉的那条原样带回来。用户看到的就是一次点击换来「删了又冒出来」。
 *
 * 两条匹配口径（缺一不可）：
 * 1. **按 id**：服务端 id 的消息，删除失败会被放回原位（同时撤销墓碑），挂起期间按 id 挡住历史里的同一条。
 * 2. **按文字 + 创建时间（±2 分钟）**：本地临时行（`local-` 前缀）被删掉时，服务端可能还留着
 *    同一条的正式版（id 完全不同）。这条口径只对**本地临时行的墓碑**生效，挡的是服务端行——
 *    两条真实存在的重复文字因此有极小概率被一起藏掉，代价是暂时的（服务端删除确认后墓碑撤销），
 *    而「删过的东西回来」是用户一定能察觉的错。窗口与 [HistoryMerge] 的匹配窗口保持一致。
 *
 * 有意保守：这里只拦「历史重建」这条复活路径，不做删除的持久化队列（重启后待删的服务端删除
 * 就丢了）。丢的那部分下次进会话会从服务端回来——那是**没有承诺**的行为，比复活已确认删掉的
 * 消息轻得多，加队列是另一件事（不在本次最小收敛范围）。
 */
internal class PendingDeletions {

    private companion object {
        /** 与 [HistoryMerge] 的匹配窗口同一条：超过这个时间差就不认为是同一条消息。 */
        const val MATCH_WINDOW_MS = 120_000L
    }

    /** 墓碑内容：只用来做「服务端正式版」那条口径的匹配。 */
    private class Tombstone(val text: String, val createdAtMillis: Long, val localOnly: Boolean)

    private val marks = ConcurrentHashMap<String, Tombstone>()

    /** 界面已经移除这条：挂墓碑，直到服务端确认删除或失败回滚。 */
    fun mark(message: ChatMessage) {
        marks[message.id] = Tombstone(
            text = message.text,
            createdAtMillis = message.createdAtMillis,
            localOnly = message.id.toLongOrNull() == null,
        )
    }

    /** 服务端已确认删除（或失败后已放回原位）：撤销墓碑，历史里的这条重新有效。 */
    fun clear(messageId: String) {
        marks.remove(messageId)
    }

    fun isEmpty(): Boolean = marks.isEmpty()

    /** 这条是不是「已经删过、正在等确认」。 */
    fun isPending(message: ChatMessage): Boolean {
        marks[message.id]?.let { return true }
        if (message.id.toLongOrNull() == null) return false
        return marks.values.any { t ->
            t.localOnly && t.text == message.text &&
                kotlin.math.abs(t.createdAtMillis - message.createdAtMillis) <= MATCH_WINDOW_MS
        }
    }

    /** 从一份（服务端或本地缓存来的）列表里剔掉正在等确认的消息。空集合时原样返回，零成本。 */
    fun filter(messages: List<ChatMessage>): List<ChatMessage> {
        if (marks.isEmpty()) return messages
        return messages.filterNot { isPending(it) }
    }
}
