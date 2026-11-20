package com.openapi.feature.chat.data.remote

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 有界并发地把 [items] 逐个交给 [transform]，返回 `key -> 结果`。
 *
 * 为什么需要它：一页历史里每张远程图片要单独调一次 `GET /uploads/{id}` 换签名 URL
 * （单张 0.4–2s），串行时切会话的关键路径是「图片数 × 单次时延」，9 张图就是最坏 18s。
 * 并发化后关键路径 ≈ 最慢的一张；[limit] 用信号量封顶，防止一页很多图时把服务端和
 * OkHttp 连接池瞬间打满（默认 8，与连接池默认并发相当）。
 *
 * 语义与并发化前的逐张 `runCatching` 一致：单个失败在该键上记为 null，
 * 绝不让一张图拉不动把整页历史抛出去；协程取消照常向上传播。
 */
internal suspend fun <K, V> boundedMap(
    items: List<K>,
    limit: Int = 8,
    transform: suspend (K) -> V,
): Map<K, V?> {
    if (items.isEmpty()) return emptyMap()
    val semaphore = Semaphore(limit.coerceAtLeast(1))
    return coroutineScope {
        items.map { key ->
            async {
                semaphore.withPermit {
                    val value = try {
                        transform(key)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        null
                    }
                    key to value
                }
            }
        }.awaitAll().toMap()
    }
}
