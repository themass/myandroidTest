package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * USER 侧贴纸词表的钉死测试（用户 2026-11-28：「贴纸你也没搞啊」）。
 *
 * 贴纸线**不复用**特效词表：贴纸按「贴在哪个已跟踪的部位」分组，
 * 钉四件事：
 * 1. 白名单 = 头顶 / 脸部 / 手持三类，每条带部位；
 * 2. 部位决定需要什么跟踪信号（脸 / 手），可用性判定按部位给原因（无人脸 / 没手）；
 * 3. 每条都有本模块 assets 里的 JSON，可解析、含 v/fr/ip/op/w/h/layers，≤100KB；
 * 4. 几何：头顶类走 headTop，脸部类走 eyeLine（墨镜压眼睛），手持类走手掌中心。
 */
class StickerWordTableTest {

    private fun <T : Any> nonNull(message: String, value: T?): T {
        assertTrue("$message（实际 null）", value != null)
        return value!!
    }

    @Test
    fun `whitelist covers head face and hand stickers with their anchor part`() {
        val expected = mapOf(
            StickerAssetIds.PAT_HEAD to StickerAnchorPart.HEAD_TOP,
            StickerAssetIds.CAT_EARS to StickerAnchorPart.HEAD_TOP,
            StickerAssetIds.CROWN to StickerAnchorPart.HEAD_TOP,
            StickerAssetIds.FLOWER_WREATH to StickerAnchorPart.HEAD_TOP,
            StickerAssetIds.SUNGLASSES to StickerAnchorPart.EYES,
            StickerAssetIds.MUSTACHE to StickerAnchorPart.MOUTH,
            StickerAssetIds.HAND_HEART to StickerAnchorPart.HAND,
        )
        assertEquals(expected.keys, StickerAssetIds.ALLOWED)
        for ((id, part) in expected) {
            val spec = nonNull("贴纸词表缺 $id", StickerAssetIds.lookup(id))
            assertEquals("$id 部位", part, spec.part)
        }
        // 贴纸线不复用舞台特效词表：光弧/流光是特效，不是贴纸。
        assertTrue("光弧不是贴纸", StickerAssetIds.lookup("fx_wave") == null)
        assertTrue("流光不是贴纸", StickerAssetIds.lookup("fx_swipe") == null)
    }

    @Test
    fun `each part needs exactly its own tracking signal`() {
        assertEquals(true, StickerAssetIds.needsFace(StickerAssetIds.CROWN))
        assertEquals(true, StickerAssetIds.needsFace(StickerAssetIds.SUNGLASSES))
        assertEquals(true, StickerAssetIds.needsFace(StickerAssetIds.MUSTACHE))
        assertEquals(false, StickerAssetIds.needsFace(StickerAssetIds.HAND_HEART))
        assertEquals(true, StickerAssetIds.needsHand(StickerAssetIds.HAND_HEART))
        assertEquals(false, StickerAssetIds.needsHand(StickerAssetIds.CROWN))
    }

    @Test
    fun `availability gives the missing signal as the reason, never hides it`() {
        val faceOnly = StickerAvailability.check(
            stickerAssetId = StickerAssetIds.HAND_HEART,
            hasFace = true,
            hasHand = false,
        )
        assertEquals(false, faceOnly.available)
        assertTrue("手持类缺手要给原因", faceOnly.reason!!.contains("手"))

        val noFace = StickerAvailability.check(
            stickerAssetId = StickerAssetIds.CROWN,
            hasFace = false,
            hasHand = true,
        )
        assertEquals(false, noFace.available)
        assertTrue("头顶类缺脸要给原因", noFace.reason!!.contains("脸"))

        val ok = StickerAvailability.check(StickerAssetIds.SUNGLASSES, hasFace = true, hasHand = false)
        assertEquals(true, ok.available)
        assertNull(ok.reason)

        // 白名单外的 id：不崩，给原因（相机侧白名单校验与面板同一口径）。
        val unknown = StickerAvailability.check("fx_nope", hasFace = true, hasHand = true)
        assertEquals(false, unknown.available)
        assertTrue(unknown.reason!!.isNotBlank())
    }

    // ---- 几何：三条部位各走各自的锚点，复用既有 landmark 换算 ---------------

    @Test
    fun `head sticker sits on headTop`() {
        val map = CanvasMap(400f, 800f, 720, 960, fill = true, mirrorX = false)
        val face = FaceFx(
            polylines = emptyList(),
            bounds = Rect2d(0.3f, 0.2f, 0.4f, 0.5f),
            headTop = Point2d(0.5f, 0.14f),
            eyeLine = Segment2d(Point2d(0.4f, 0.3f), Point2d(0.6f, 0.3f)),
            tiltDeg = 0f,
        )
        val anchor = stickerAnchorFor(StickerAssetIds.CROWN, face, hand = null)
        assertEquals(Point2d(0.5f, 0.14f), anchor)
    }

    @Test
    fun `sunglasses sit on the eye line midpoint and follow its tilt`() {
        val face = FaceFx(
            polylines = emptyList(),
            bounds = Rect2d(0.3f, 0.2f, 0.4f, 0.5f),
            headTop = Point2d(0.5f, 0.14f),
            eyeLine = Segment2d(Point2d(0.4f, 0.30f), Point2d(0.6f, 0.34f)),
            tiltDeg = 0f,
        )
        val anchor = nonNull("墨镜锚点为 null", stickerAnchorFor(StickerAssetIds.SUNGLASSES, face, hand = null))
        assertEquals(0.5f, anchor.x, 1e-4f)
        assertEquals(0.32f, anchor.y, 1e-4f)
        // 宽按两眼距换算（不是脸宽）——墨镜宽度跟着眼距走才不会盖到耳朵。
        assertEquals(0.204f, eyeDistanceNorm(face), 1e-3f)
        // 倾角与 HUD 同一口径（前摄镜像反号）。
        val tilt = lineTiltDegrees(Point2d(0.4f, 0.30f), Point2d(0.6f, 0.34f), mirrorX = true)
        assertTrue("镜像后应为负倾角", tilt < 0f)
    }

    @Test
    fun `mustache sits on the mouth anchor`() {
        val face = FaceFx(
            polylines = emptyList(),
            bounds = Rect2d(0.3f, 0.2f, 0.25f, 0.4f),
            headTop = Point2d(0.425f, 0.152f),
            eyeLine = Segment2d(Point2d(0.35f, 0.30f), Point2d(0.50f, 0.30f)),
            tiltDeg = 0f,
            mouth = Point2d(0.425f, 0.44f),
        )
        val anchor = stickerAnchorFor(StickerAssetIds.MUSTACHE, face, hand = null)
        // 唇锚点由既有唇部 landmark（FACE_LIPS_OUTER）均值算出，挂在 FaceFx.mouth 上。
        assertEquals(Point2d(0.425f, 0.44f), anchor)
    }

    @Test
    fun `mouth anchor is the mean of the outer lip landmarks`() {
        val pts = listOf(Point2d(0.3f, 0.4f), Point2d(0.5f, 0.5f), Point2d(0.7f, 0.4f))
        val m = mouthAnchor(pts)!!
        assertEquals(0.5f, m.x, 1e-5f)
        assertEquals(0.4333f, m.y, 1e-3f)
        assertNull(mouthAnchor(emptyList()))
    }

    @Test
    fun `frame sources follow the part and drop untracked entries`() {
        val face = FaceFx(
            polylines = emptyList(),
            bounds = Rect2d(0.3f, 0.2f, 0.4f, 0.5f),
            headTop = Point2d(0.5f, 0.14f),
            eyeLine = Segment2d(Point2d(0.4f, 0.30f), Point2d(0.6f, 0.30f)),
            tiltDeg = 6f,
            mouth = Point2d(0.5f, 0.44f),
        )
        val faceFrame = FrameFx(listOf(face), emptyList(), emptyList(), 720, 960, 1L, 20f)
        assertEquals(1, stickerAnchorsForFrame(StickerAssetIds.CROWN, faceFrame).size)
        // 墨镜宽度按眼距（0.2）而不是脸宽（0.4）。
        val glasses = stickerAnchorsForFrame(StickerAssetIds.SUNGLASSES, faceFrame).single()
        assertTrue("墨镜应比头饰窄", glasses.widthNorm < stickerAnchorsForFrame(StickerAssetIds.CROWN, faceFrame).single().widthNorm)
        // 手持类没有手 → 一路都没有（不猜位置）。
        assertEquals(0, stickerAnchorsForFrame(StickerAssetIds.HAND_HEART, faceFrame).size)

        val hand = HandFx(
            segments = emptyList(),
            gesture = HandGestureKind.FIST,
            points = (0 until HAND_LANDMARK_COUNT).map { Point2d(0.5f + it * 0.001f, 0.6f) },
        )
        val handFrame = FrameFx(emptyList(), emptyList(), listOf(hand), 720, 960, 1L, 20f)
        val handSource = stickerAnchorsForFrame(StickerAssetIds.HAND_HEART, handFrame).single()
        assertEquals(0.6f, handSource.anchor.y, 1e-3f)
        assertTrue("手持类要有正尺寸", handSource.widthNorm > 0f)
        // 头顶类不看手。
        assertEquals(0, stickerAnchorsForFrame(StickerAssetIds.CROWN, handFrame).size)
    }

    @Test
    fun `hand sticker sits at the palm centre of the tracked hand`() {
        val hand = HandFx(
            segments = emptyList(),
            gesture = HandGestureKind.FIST,
            points = (0 until HAND_LANDMARK_COUNT).map { Point2d(0.5f, 0.6f) },
        )
        val anchor = nonNull("手持锚点为 null", stickerAnchorFor(StickerAssetIds.HAND_HEART, face = null, hand = hand))
        // 均值是浮点累加（0.6f 不是二进制精确值），只能按容差比——0.5/0.6 的语义才是这里钉的。
        assertEquals(0.5f, anchor.x, 1e-5f)
        assertEquals(0.6f, anchor.y, 1e-5f)
        // 手不在画面 → null（贴纸消失，不猜位置）。
        assertNull(stickerAnchorFor(StickerAssetIds.HAND_HEART, face = null, hand = null))
        // 点不足 21 → null（与滑动跟踪同纪律）。
        val short = HandFx(segments = emptyList(), gesture = HandGestureKind.NONE, points = List(10) { Point2d(0f, 0f) })
        assertNull(stickerAnchorFor(StickerAssetIds.HAND_HEART, face = null, hand = short))
    }

    // ---- 资产结构 -----------------------------------------------------------

    private val assetsDir: File by lazy {
        val direct = File("src/main/assets/fx")
        if (direct.isDirectory) direct else File(repoRoot(), "feature-camera-fx/src/main/assets/fx")
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        return checkNotNull(dir)
    }

    @Test
    fun `every whitelisted sticker json exists parses and carries the required keys`() {
        assertTrue("找不到资产目录 $assetsDir", assetsDir.isDirectory)
        for (id in StickerAssetIds.ALLOWED) {
            val f = File(assetsDir, "$id.json")
            assertTrue("缺贴纸资产：$id", f.isFile)
            assertTrue("$id 超过 100KB", f.length() <= 100_000L)
            val root = com.google.gson.JsonParser.parseString(f.readText()).asJsonObject
            assertTrue("$id v 必须是版本号字符串", root.get("v").isJsonPrimitive && root.get("v").asString.isNotBlank())
            assertEquals("$id nm", id, root.get("nm").asString)
            for (key in listOf("fr", "ip", "op", "w", "h")) {
                assertTrue("$id $key", root.get(key).isJsonPrimitive)
            }
            assertTrue("$id w>0", root.get("w").asInt > 0)
            assertTrue("$id h>0", root.get("h").asInt > 0)
            assertTrue("$id 有图层", root.getAsJsonArray("layers").size() > 0)
            val text = f.readText()
            for (banned in listOf("\"ty\":\"img\"", "\"ty\":\"tt\"", "\"ty\":\"kd\"", "\"tx\":\"ex\"", "\"ddd\":1")) {
                assertTrue("$id 含禁用结构 $banned", !text.contains(banned))
            }
        }
    }

    @Test
    fun `overlay policy accepts every whitelisted sticker`() {
        for (id in StickerAssetIds.ALLOWED) {
            val p = CameraFxOverlayPolicy(stickerAssetId = id)
            assertEquals("fx/$id.json", p.lottieAssetPath)
        }
    }
}
