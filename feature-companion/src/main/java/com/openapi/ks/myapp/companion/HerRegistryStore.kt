package com.openapi.ks.myapp.companion

/**
 * her 注册表的进程内缓存（客户端第二刀）。
 *
 * 失败宽出：拉取失败返回**过期缓存**（没有缓存就返回空表）并记日志——陪伴页不能因为
 * her 注册表不可用而白屏；空表在 [com.openapi.ks.myapp.dh.resolveCallStage] 里自然
 * 回落到现在的默认舞台。服务端没上这一刀时接口默认实现直接回空页，连失败都不产生。
 */
class HerRegistryStore(
    private val api: CompanionApi,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    /** 可注入时钟：单测钉 TTL 行为，不靠真实时间睡。 */
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var cached: HerPage? = null
    private var fetchedAt = 0L

    /** 该角色可用的 her 摘要列表：TTL 内复用缓存，失败回退（见类注释）。 */
    suspend fun hers(): List<HerSummary> {
        val hit = cached
        if (hit != null && nowMillis() - fetchedAt < ttlMillis) return hit.items
        return runCatching { api.hers() }
            .onSuccess { page ->
                cached = page
                fetchedAt = nowMillis()
            }
            .fold(
                onSuccess = { it.items },
                onFailure = { error ->
                    val fallback = if (hit != null) "过期缓存" else "空表"
                    android.util.Log.i(TAG, "her 注册表拉取失败，回退$fallback: ${error.message}")
                    hit?.items.orEmpty()
                },
            )
    }

    private companion object {
        const val TAG = "HerRegistry"
        const val DEFAULT_TTL_MILLIS = 5 * 60_000L
    }
}
