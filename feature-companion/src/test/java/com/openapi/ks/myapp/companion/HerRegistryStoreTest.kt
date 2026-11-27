package com.openapi.ks.myapp.companion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * her 注册表的读取与缓存（客户端第二刀）。钉两条纪律：
 * 1. **失败宽出**：网络失败返回缓存（可能过期）或空表，绝不抛——陪伴页不能因为
 *    her 注册表拉不到而白屏（引擎选择自动回落到现默认路径）；
 * 2. 一次进房只打一次 `/v1/hers`：TTL 内复用缓存。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HerRegistryStoreTest {

    private class FakeHerApi(
        /** 第 n 次调用（从 0 起）的返回；列表耗尽后重复最后一项。 */
        private val results: List<Result<HerPage>>,
    ) : CompanionApi {
        var calls = 0
        val seen = mutableListOf<Pair<Int, Int>>()

        override suspend fun hers(limit: Int, offset: Int): HerPage {
            val r = results[minOf(calls, results.lastIndex)]
            calls++
            seen += limit to offset
            return r.getOrThrow()
        }

        override suspend fun createSession(characterId: String) = error("unused")
        override suspend fun createScriptSession(characterId: String, caseId: String) = error("unused")
        override suspend fun history(sessionId: Long, limit: Int) = error("unused")
        override suspend fun attachmentUrl(uploadId: String) = error("unused")
        override suspend fun characters(limit: Int, offset: Int) = error("unused")
        override suspend fun character(id: String) = error("unused")
        override suspend fun memory(roleId: String, limit: Int, offset: Int) = error("unused")
        override suspend fun deleteMemory(roleId: String, id: String) = error("unused")
        override suspend fun profileDoc(roleId: String) = error("unused")
        override suspend fun updateProfileDoc(roleId: String, markdown: String) = error("unused")
        override suspend fun events(roleId: String, includePast: Boolean) = error("unused")
        override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String) = error("unused")
        override suspend fun setEventStatus(id: String, status: String) = error("unused")
        override suspend fun deleteEvent(id: String) = error("unused")
        override suspend fun pendingActions() = error("unused")
        override suspend fun confirmAction(confirmationId: String, accepted: Boolean) = error("unused")
        override suspend fun preferences() = error("unused")
        override suspend fun updatePreferences(patch: Map<String, Any?>) = error("unused")
        override suspend fun inbox() = error("unused")
    }

    private fun page(vararg ids: String) = HerPage(
        items = ids.map { HerSummary(id = it, name = "", tagline = "", renderer = "simple", status = "published") },
        total = ids.size,
    )

    private fun store(api: CompanionApi, clock: () -> Long = { 0L }) =
        HerRegistryStore(api, nowMillis = clock, ttlMillis = 60_000)

    @Test
    fun `first read hits the api and caches for the ttl`() {
        val api = FakeHerApi(listOf(Result.success(page("simple-linche-v1"))))
        var now = 0L
        val s = store(api) { now }
        assertEquals(listOf("simple-linche-v1"), runBlocking { s.hers() }.map { it.id })
        now = 30_000
        assertEquals(listOf("simple-linche-v1"), runBlocking { s.hers() }.map { it.id })
        assertEquals("TTL 内不应再打服务端", 1, api.calls)
        now = 61_000
        runBlocking { s.hers() }
        assertEquals("TTL 过期后要重新拉", 2, api.calls)
    }

    @Test
    fun `a failed fetch falls back to the cached page, even if stale`() {
        val api = FakeHerApi(
            listOf(
                Result.success(page("simple-linche-v1")),
                Result.failure(CompanionApiException("陪伴服务暂时不可用", statusCode = 503)),
            ),
        )
        var now = 0L
        val s = store(api) { now }
        runBlocking { s.hers() }
        now = 120_000
        val fallback = runBlocking { s.hers() }
        assertEquals(listOf("simple-linche-v1"), fallback.map { it.id })
        assertEquals(2, api.calls)
    }

    @Test
    fun `with no cache and a failed fetch the store returns empty and never throws`() {
        val api = FakeHerApi(
            listOf(Result.failure(CompanionApiException("陪伴服务暂时不可用，请稍后再试", statusCode = 502))),
        )
        val s = store(api)
        assertEquals(emptyList<String>(), runBlocking { s.hers() }.map { it.id })
    }

    @Test
    fun `an endpoint the server does not have yet yields an empty registry`() {
        // 接口默认实现（老服务端没有 hers）：不覆写的实现直接回空页——调用方因此永远拿到 List。
        val api = object : CompanionApi {
            override suspend fun createSession(characterId: String) = error("unused")
            override suspend fun createScriptSession(characterId: String, caseId: String) = error("unused")
            override suspend fun history(sessionId: Long, limit: Int) = error("unused")
            override suspend fun attachmentUrl(uploadId: String) = error("unused")
            override suspend fun characters(limit: Int, offset: Int) = error("unused")
            override suspend fun character(id: String) = error("unused")
            override suspend fun memory(roleId: String, limit: Int, offset: Int) = error("unused")
            override suspend fun deleteMemory(roleId: String, id: String) = error("unused")
            override suspend fun profileDoc(roleId: String) = error("unused")
            override suspend fun updateProfileDoc(roleId: String, markdown: String) = error("unused")
            override suspend fun events(roleId: String, includePast: Boolean) = error("unused")
            override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String) = error("unused")
            override suspend fun setEventStatus(id: String, status: String) = error("unused")
            override suspend fun deleteEvent(id: String) = error("unused")
            override suspend fun pendingActions() = error("unused")
            override suspend fun confirmAction(confirmationId: String, accepted: Boolean) = error("unused")
            override suspend fun preferences() = error("unused")
            override suspend fun updatePreferences(patch: Map<String, Any?>) = error("unused")
            override suspend fun inbox() = error("unused")
        }
        assertEquals(0, runBlocking { api.hers() }.total)
        assertEquals(emptyList<String>(), runBlocking { HerRegistryStore(api).hers() }.map { it.id })
    }
}
