package com.openapi.feature.chat.data.remote

import com.openapi.core.model.ChatLog
import com.openapi.feature.chat.data.mapper.ChatLogMapper
import com.openapi.feature.chat.domain.model.DeliveryStatus

/**
 * 从一批本地行里汇出「还不能当垃圾删」的本机附件路径（复审 Important 的数据侧唯一判据）。
 *
 * 两个本地缓存共用这一份：[LocalChatStore]（可注入库，测试用）与 [MessageCache]（全局库）
 * 以前各写一遍同样的循环，一字不差——两份份本会漂（复审 nit 4）。取行本身（查库、
 * `runCatching`、按 `local-` 前缀）仍留在调用方，因为两边拿库的方式不同，
 * 且 MessageCache 那条查不动时要回空集合而不是把一次选图打成异常。
 *
 * 判据：
 * - **非 SENT 的行留着**：一条 `local-` 行如果没到 SENT（上传一次都没成功过），
 *   它没有可用的 `uploadId`（服务端只认 10 分钟内签发的那张，ADR-011），
 *   重发只能重新上传本机字节；扫掉 = 那条消息永久发不出去。
 * - **SENT 的行不留**：服务端历史里一定有它的正式版，图没了也能走签名 URL，那些字节是真孤儿。
 *
 * 宽出来的部分写清楚（复审 nit 3）：取行按 `chatId LIKE 'local-%'`，
 * 于是 `local-assistant-*` / `local-pending-*`（助手占位）与 `local-voice-*` 也在批里 ——
 * 这与 [LocalChatStore.localPending] 的判据**不完全相同**（那条还要 `role == USER`）。
 * 这里刻意不加 role 谓词：助手占位行没有附件、voice 行的字节在 `cache/voice` 而不是暂存目录，
 * 多筛一遍不会改变结果，只会让两处判据看着一致其实各写各的。
 * 最坏的后果是**过留**（多留一轮 GC），永远不是误删。
 */
internal fun keptAttachmentPaths(rows: List<ChatLog>): Set<String> {
    val out = LinkedHashSet<String>()
    rows.forEach { log ->
        val message = ChatLogMapper.toDomain(
            chatId = log.chatId,
            who = log.who,
            msgType = log.msgType,
            rawContent = log.content,
            createdAtMillis = log.createTime?.time ?: 0L,
        ) ?: return@forEach
        if (message.delivery == DeliveryStatus.SENT) return@forEach
        out += ChatLogMapper.localAttachmentPaths(message)
    }
    return out
}
