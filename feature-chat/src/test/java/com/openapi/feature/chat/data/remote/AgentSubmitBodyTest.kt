package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.GeoLocation
import com.openapi.feature.chat.domain.model.MessageKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2-13 协议形状钉桩（org.json 在 JVM 单测里是 stub，必须走 Robolectric）：
 * - 发送体：location 是**顶层字段**，全空发送不能带空壳（服务端 422 empty_message 的形态约束）；
 * - 历史体：location 对象、视频附件的 duration_s 解析，缺字段和坏字段都要优雅退化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AgentSubmitBodyTest {

    @Test
    fun locationOnlyBodyCarriesTopLevelLocationAndNoAttachmentsKey() {
        val location = buildAgentLocationJson(31.23, 121.47, 120)
        val body = buildAgentSubmitBody("", "cid-1", attachments = null, location = location)

        assertEquals("", body.getString("text"))
        assertEquals("cid-1", body.getString("client_message_id"))
        assertTrue(body.has("location"))
        // 空壳 attachments 同样是形态错误：没附件就整个 key 不带
        assertTrue(!body.has("attachments"))
        val loc = body.getJSONObject("location")
        assertEquals(31.23, loc.getDouble("lat"), 1e-9)
        assertEquals(121.47, loc.getDouble("lng"), 1e-9)
        assertEquals(120, loc.getInt("accuracy_m"))
    }

    @Test
    fun locationWithoutAccuracyOmitsTheKeyInsteadOfClaimingZeroMeters() {
        val loc = buildAgentLocationJson(31.23, 121.47, accuracyMeters = null)
        assertTrue(!loc.has("accuracy_m"))
    }

    @Test
    fun plainTextBodyHasNeitherLocationNorAttachments() {
        val body = buildAgentSubmitBody("你好", "cid-2", attachments = null, location = null)
        assertEquals("你好", body.getString("text"))
        assertTrue(!body.has("location") && !body.has("attachments"))
    }

    @Test
    fun videoAttachmentCarriesDurationAndKind() {
        val attachments = JSONArray()
            .put(JSONObject().put("upload_id", "u1").put("kind", "video").put("name", "a.mp4").put("duration_s", 42))
        val body = buildAgentSubmitBody("看这个", "cid-3", attachments = attachments, location = null)
        val sent = body.getJSONArray("attachments").getJSONObject(0)
        assertEquals("video", sent.getString("kind"))
        assertEquals(42, sent.getInt("duration_s"))
    }

    // ---------------------------------------------------------------- 历史解析

    @Test
    fun historyLocationRowMapsLatLngAccuracy() {
        val vo = JSONObject(
            """{"id":10,"role":"user","content":"[位置] 31.23,121.47 (±120m)","kind":"location",
               "created_at":"2026-11-20T08:30:00Z",
               "location":{"lat":31.23,"lng":121.47,"accuracy_m":120}}""",
        ).toAgentMessageVo()
        assertEquals("location", vo.kind)
        assertEquals(31.23, vo.location!!.lat, 1e-9)
        assertEquals(121.47, vo.location.lng, 1e-9)
        assertEquals(120, vo.location.accuracyMeters)
    }

    @Test
    fun historyLocationWithoutAccuracyMapsToNullAccuracy() {
        val vo = JSONObject(
            """{"id":11,"role":"user","content":"","kind":"location","created_at":"2026-11-20T08:30:00Z",
               "location":{"lat":31.2,"lng":121.4}}""",
        ).toAgentMessageVo()
        assertEquals(null, vo.location!!.accuracyMeters)
    }

    @Test
    fun historyLocationWithBrokenCoordinatesMapsToNull() {
        val vo = JSONObject(
            """{"id":12,"role":"user","content":"x","kind":"location","created_at":"2026-11-20T08:30:00Z",
               "location":{"lat":"abc","lng":121.4}}""",
        ).toAgentMessageVo()
        // 坏坐标不能画成一个 (0,0) 气泡
        assertNull(vo.location)
    }

    @Test
    fun historyVideoAttachmentReadsDurationFromMetaAndNormalizesOutOfRange() {
        val vo = JSONObject(
            """{"id":13,"role":"user","content":"","kind":"chat","created_at":"2026-11-20T08:30:00Z",
               "attachments":[
                 {"upload_id":"v1","kind":"video","name":"a.mp4","meta":{"duration_s":42}},
                 {"upload_id":"v2","kind":"video","name":"b.mp4","duration_s":7},
                 {"upload_id":"v3","kind":"video","name":"c.mp4","duration_s":99999}
               ]}""",
        ).toAgentMessageVo()
        val videos = vo.attachments
        assertEquals(42, videos[0].durationSeconds)
        // 顶层 duration_s 也要认（服务端契约的扁平形态）
        assertEquals(7, videos[1].durationSeconds)
        // 超出 0..86400 的脏数据归一成 null，而不是显示 27 小时
        assertNull(videos[2].durationSeconds)
    }

    /** A40（用户 2026-11-26）：历史附件 kind="img" 要在解析边界归一成 "image"。 */
    @Test
    fun historyImgAttachmentNormalizesToImageKind() {
        val vo = JSONObject(
            """{"id":15,"role":"user","content":"","kind":"chat","created_at":"2026-11-20T08:30:00Z",
               "attachments":[{"upload_id":"i1","kind":"img","name":"a.jpg"}]}""",
        ).toAgentMessageVo()
        assertEquals("image", vo.attachments.single().kind)
        assertEquals(MessageKind.IMAGE, vo.toChatMessage().kind)
    }

    /** VO → 领域模型：location 行必须带上坐标并判成 LOCATION；视频附件进 videos。 */
    @Test
    fun voMapsToDomainLocationAndVideos() {
        val vo = JSONObject(
            """{"id":14,"role":"user","content":"到这儿","kind":"chat","created_at":"2026-11-20T08:30:00Z",
               "attachments":[{"upload_id":"v1","kind":"video","name":"a.mp4","duration_s":10}],
               "location":{"lat":31.23,"lng":121.47,"accuracy_m":50}}""",
        ).toAgentMessageVo().toChatMessage()
        assertEquals(MessageKind.LOCATION, vo.kind)
        assertEquals(GeoLocation(31.23, 121.47, 50), vo.location)
        assertEquals(1, vo.videos.size)
        assertEquals("a.mp4", vo.videos.single().name)
        assertEquals(10, vo.videos.single().durationSeconds)
        assertEquals("v1", vo.videos.single().uploadId)
        // 历史视频刻意不预取播放 URL：path 是空串，点开那一刻才换
        assertEquals("", vo.videos.single().path)
    }
}
