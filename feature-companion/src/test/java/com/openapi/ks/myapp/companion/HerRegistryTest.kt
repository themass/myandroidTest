package com.openapi.ks.myapp.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `/v1/hers` 摘要（服务端第一刀 ca_her_pack）到客户端模型的解析。
 * 用 Robolectric 跑，保证 org.json 是真实实现（与 CompanionModelsTest 同款）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HerRegistryTest {

    @Test
    fun `her summary parses the production list shape`() {
        val json = JSONObject(
            """
            {
              "id": "simple-linche-v1",
              "name": "林澈",
              "tagline": "冷静务实",
              "renderer": "simple",
              "status": "published",
              "pack_version": 3,
              "effect_count": 4,
              "sticker_count": 2,
              "voice_ref": {"provider": "volcengine", "voice_id": "zh_female"}
            }
            """.trimIndent(),
        )
        val her = HerSummary.from(json)
        assertEquals("simple-linche-v1", her.id)
        assertEquals("林澈", her.name)
        assertEquals("冷静务实", her.tagline)
        assertEquals("simple", her.renderer)
        assertEquals("published", her.status)
        assertEquals(3, her.packVersion)
        assertEquals(4, her.effectCount)
        assertEquals(2, her.stickerCount)
        assertEquals("zh_female", her.voiceId)
    }

    @Test
    fun `missing optional fields degrade to blanks and zeros, never a throw`() {
        val her = HerSummary.from(JSONObject())
        assertEquals("", her.id)
        assertEquals("", her.renderer)
        assertEquals(0, her.packVersion)
        assertNull(her.voiceId)
    }
}
