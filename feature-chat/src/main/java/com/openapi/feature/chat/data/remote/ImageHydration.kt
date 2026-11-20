package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ImageSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 把一页历史里的远程图片换成可渲染的签名 URL（每张一次 `GET /uploads/{id}`，服务端给 300 秒）。
 *
 * **为什么必须并发**：这一步在「切换会话」的关键路径上——签名 URL 是渲染的前置条件，
 * 页面要等它才能画。一次请求 0.4–2 秒，以前用 `associateWith` 逐张串行取，
 * 一页 5 张图就是 2–10 秒白屏。`coroutineScope` 里一起发出去后，整页只等最慢的一张。
 *
 * 两条旧语义原样保留：
 * - 同一 upload_id 在一页里出现多次只取一次（`distinct`）；
 * - 单张失败退化为空路径，不拖垮整页——[fetch] 返回 null 即视为失败。
 *
 * 没做并发上限：一页最多 30 条消息、图通常个位数，为它引入限流器只是多一层会出错的代码。
 * 若以后一页图多到一个连接池吃不下，在这里加 semaphore，而不是回到串行。
 */
internal suspend fun hydrateRemoteImages(
    messages: List<ChatMessage>,
    fetch: suspend (String) -> String?,
): List<ChatMessage> {
    val ids = messages
        .flatMap { m -> m.images.filter { it.source == ImageSource.REMOTE }.mapNotNull { it.uploadId } }
        .distinct()
    if (ids.isEmpty()) return messages
    val urls = coroutineScope {
        ids.associateWith { id -> async { runCatching { fetch(id) }.getOrNull() } }
            .mapValues { (_, deferred) -> deferred.await() }
    }
    return messages.map { m ->
        if (m.images.none { it.source == ImageSource.REMOTE }) m
        else m.copy(images = m.images.map { img ->
            if (img.source != ImageSource.REMOTE) img
            else img.copy(path = img.uploadId?.let { urls[it] }.orEmpty())
        })
    }
}
