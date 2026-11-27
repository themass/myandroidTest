package com.openapi.ks.myapp.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `GET /v1/hers/{id}` 详情 JSON 到 [HerDetail] 的解析（服务端第二刀契约）。
 * Robolectric 跑，org.json 是真实实现（与 [HerRegistryTest] 同款）。
 *
 * 钉四件事：
 * 1. **契约快照**：顶层 / model_ref / 装饰条目的键集合钉死成下面的常量表——服务端
 *    （agent-docs 仓库）有同款快照测试，两边改字段必须一起改，漂移当场红；
 * 2. 缺键一律退化成空值 / 空列表，绝不抛（老服务端 / 坏数据）；
 * 3. hash 非 64 hex = 不可下载（校验无从做起就不下）；
 * 4. [HerDetail.asSummary] 的计数以详情清单为准。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HerDetailTest {

    /** 完整的生产形状 fixture（与服务端第二刀绑定契约逐键对应）。 */
    private val fixture = """
        {
          "id": "live2d-linche-v2",
          "name": "林澈 · Live2D",
          "tagline": "会动的形象",
          "renderer": "live2d",
          "status": "published",
          "pack_version": 2,
          "source": "seed",
          "thumbnail_url": "https://cdn.test/thumb?sig=abc",
          "portrait_url": "https://cdn.test/portrait?sig=def",
          "url_expires_in": 3600,
          "model_ref": {
            "asset": "model/scene.model3.json",
            "version": 2,
            "hash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "download_url": "https://cdn.test/pack.zip?sig=ghi",
            "url_expires_in": 3600,
            "size_bytes": 12345678
          },
          "effects": [
            {"id": "fx_wave", "name": "海浪", "anchor": "HEAD", "hash": "aaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccdddd", "size": 4096, "url": "https://cdn.test/fx?sig=j", "url_expires_in": 3600}
          ],
          "stickers": [
            {"id": "st_bow", "name": "蝴蝶结", "anchor": "HEAD", "hash": "bbbbccccddddeeeebbbbccccddddeeeebbbbccccddddeeeebbbbccccddddeeee", "size": 2048, "url": "https://cdn.test/st?sig=k", "url_expires_in": 3600}
          ],
          "voice_ref": {"provider": "volcengine", "voice_id": "zh_female"},
          "persona": {"role_id": "linche", "name": "林澈", "description": "冷静务实", "personality": "外冷内热"}
        }
    """.trimIndent()

    // ---- 1. 契约快照：与服务端逐键对齐的键表（改字段 = 两边一起改） ----

    private val topLevelKeys = setOf(
        "id", "name", "tagline", "renderer", "status", "pack_version", "source",
        "thumbnail_url", "portrait_url", "url_expires_in",
        "model_ref", "effects", "stickers", "voice_ref", "persona",
    )
    private val modelRefKeys = setOf("asset", "version", "hash", "download_url", "url_expires_in", "size_bytes")
    private val assetItemKeys = setOf("id", "name", "anchor", "hash", "size", "url", "url_expires_in")

    @Test
    fun `contract snapshot pins the wire keys of the detail shape`() {
        val json = JSONObject(fixture)
        assertEquals(topLevelKeys, json.keys().asSequence().toSet())
        assertEquals(modelRefKeys, json.getJSONObject("model_ref").keys().asSequence().toSet())
        assertEquals(assetItemKeys, json.getJSONArray("effects").getJSONObject(0).keys().asSequence().toSet())
        assertEquals(assetItemKeys, json.getJSONArray("stickers").getJSONObject(0).keys().asSequence().toSet())
        assertEquals(
            setOf("provider", "voice_id"),
            json.getJSONObject("voice_ref").keys().asSequence().toSet(),
        )
        assertEquals(
            setOf("role_id", "name", "description", "personality"),
            json.getJSONObject("persona").keys().asSequence().toSet(),
        )
    }

    @Test
    fun `every pinned wire key lands in the model`() {
        val d = HerDetail.from(JSONObject(fixture))
        assertEquals("live2d-linche-v2", d.id)
        assertEquals("林澈 · Live2D", d.name)
        assertEquals("会动的形象", d.tagline)
        assertEquals("live2d", d.renderer)
        assertEquals("published", d.status)
        assertEquals(2, d.packVersion)
        assertEquals("seed", d.source)
        assertEquals("https://cdn.test/thumb?sig=abc", d.thumbnailUrl)
        assertEquals("https://cdn.test/portrait?sig=def", d.portraitUrl)
        assertEquals(3600, d.urlExpiresIn)
        assertEquals("model/scene.model3.json", d.model.asset)
        assertEquals(2, d.model.version)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            d.model.hash,
        )
        assertEquals("https://cdn.test/pack.zip?sig=ghi", d.model.downloadUrl)
        assertEquals(3600, d.model.urlExpiresIn)
        assertEquals(12345678L, d.model.sizeBytes)
        assertTrue(d.model.downloadable)
        assertEquals(listOf("fx_wave"), d.effects.map { it.id })
        assertEquals("海浪", d.effects.first().name)
        assertEquals("HEAD", d.effects.first().anchor)
        assertEquals(4096L, d.effects.first().sizeBytes)
        assertEquals(listOf("st_bow"), d.stickers.map { it.id })
        assertEquals("volcengine", d.voiceRef?.provider)
        assertEquals("zh_female", d.voiceRef?.voiceId)
        assertEquals("linche", d.persona?.roleId)
        assertEquals("冷静务实", d.persona?.description)
        assertEquals("外冷内热", d.persona?.personality)
    }

    // ---- 2. 缺键退化 ----

    @Test
    fun `empty object degrades to blanks and empty lists, never a throw`() {
        val d = HerDetail.from(JSONObject())
        assertEquals("", d.id)
        assertEquals("", d.portraitUrl)
        assertEquals(0, d.urlExpiresIn)
        assertEquals(HerModelRef.EMPTY, d.model)
        assertFalse(d.model.downloadable)
        assertTrue(d.effects.isEmpty())
        assertTrue(d.stickers.isEmpty())
        assertNull(d.voiceRef)
        assertNull(d.persona)
    }

    @Test
    fun `non-object entries inside effects are skipped, not fatal`() {
        val d = HerDetail.from(
            JSONObject("""{"id":"x","effects":[{},42,{"id":"fx_wave"}]}"""),
        )
        // {} 也是对象：id 空照样收成空条目；42 不是对象，跳过。
        assertEquals(2, d.effects.size)
        assertEquals("fx_wave", d.effects[1].id)
    }

    // ---- 3. hash 形状 = 可下载的门槛 ----

    @Test
    fun `hash that is not 64 hex makes the pack undownloadable`() {
        val base = HerModelRef.from(JSONObject(fixture).getJSONObject("model_ref"))
        assertTrue(base.hashUsable)

        val broken = base.copy(hash = "deadbeef")
        assertFalse(broken.hashUsable)
        assertFalse(broken.downloadable)

        assertFalse(isSha256Hex(""))
        assertFalse(isSha256Hex("z1234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        assertTrue(isSha256Hex("A".repeat(64)))   // 大小写都行（存进来时服务端给小写）
    }

    @Test
    fun `empty download url is unavailable but the rest of the detail still parses`() {
        val json = JSONObject(fixture).put(
            "model_ref",
            JSONObject(fixture).getJSONObject("model_ref").put("download_url", ""),
        )
        val d = HerDetail.from(json)
        assertEquals("林澈 · Live2D", d.name)
        assertFalse(d.model.downloadable)
    }

    // ---- 4. asSummary：计数以详情清单为准 ----

    @Test
    fun `asSummary counts decorations from the detail lists`() {
        val d = HerDetail.from(JSONObject(fixture))
        val s = d.asSummary()
        assertEquals("live2d-linche-v2", s.id)
        assertEquals(1, s.effectCount)
        assertEquals(1, s.stickerCount)
        assertEquals("zh_female", s.voiceId)
        assertEquals("https://cdn.test/thumb?sig=abc", s.thumbnailUrl)
        assertEquals(3600, s.urlExpiresIn)
    }
}
