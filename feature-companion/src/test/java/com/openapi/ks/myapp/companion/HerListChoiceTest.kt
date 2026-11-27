package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.dh.DhEngineId
import com.openapi.ks.myapp.dh.StageChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * her 列表页（OpenDesign 02「她长什么样」）的纯决策。钉四条纪律：
 *
 * 1. **只列能证明属于这个角色的 her**：id 桥约定 `<renderer>-<role>-vN`
 *    （见 [com.openapi.ks.myapp.dh.roleKeyFromHerId]）。端上不维护角色↔her 对照表，
 *    证明不了就不列——列一张别人的形象给用户点，比列表空着更糟。
 * 2. **草稿与未接入的 renderer 都不可选**：只有发布态能驱动舞台（与 resolveCallStage
 *    的 published 闸同一语义）；端上映射不出引擎的 renderer，点它写不进任何有效选择，
 *    所以那张卡只读。绝不因为「卡片在列表里」就假装能换。
 * 3. **高亮真源仍是 [StageChoice]，不是 her id**：三态里只存引擎不存 her id，所以
 *    Unset 时高亮落在服务端默认那一张（标「默认跟随」），显式选择时落在第一张能产生
 *    该舞台的卡上。不新建第二份选择状态。
 * 4. 文案只报服务端真给过的数字（版本号 / FX / 贴纸数），缺了就整段不显示。
 */
class HerListChoiceTest {

    private fun her(
        id: String,
        renderer: String,
        status: String = "published",
        name: String = "",
        tagline: String = "",
        packVersion: Int = 0,
        effectCount: Int = 0,
        stickerCount: Int = 0,
    ) = HerSummary(
        id = id,
        name = name,
        tagline = tagline,
        renderer = renderer,
        status = status,
        packVersion = packVersion,
        effectCount = effectCount,
        stickerCount = stickerCount,
    )

    // ---- 列表内容：谁能出现在这个角色的列表里 ----

    @Test
    fun `only hers provably bound to the role are listed`() {
        val hers = listOf(
            her("simple-linche-v1", "simple"),
            her("simple-sudai-v1", "simple"),
            // 命名不合播种约定（<renderer>-<asset>，无 -vN）：证明不了属于谁，不列。
            her("simple-demo-avatar-v1", "simple"),
        )
        assertEquals(
            listOf("simple-linche-v1"),
            hersForRole(hers, roleId = "linche").map { it.id },
        )
    }

    @Test
    fun `a role with no her and a missing role both get an empty list`() {
        val hers = listOf(her("simple-sudai-v1", "simple"))
        assertEquals(emptyList<String>(), hersForRole(hers, roleId = "nobody").map { it.id })
        assertEquals(emptyList<String>(), hersForRole(hers, roleId = null).map { it.id })
        assertEquals(emptyList<String>(), hersForRole(hers, roleId = "  ").map { it.id })
    }

    @Test
    fun `drafts are listed but sorted after the published ones`() {
        val hers = listOf(
            her("live2d-linche-v2", "live2d", status = "draft"),
            her("simple-linche-v1", "simple"),
            her("vrm-linche-v3", "vrm"),
        )
        assertEquals(
            listOf("simple-linche-v1", "vrm-linche-v3", "live2d-linche-v2"),
            hersForRole(hers, roleId = "linche").map { it.id },
        )
    }

    // ---- 可选性：点了能不能兑现 ----

    @Test
    fun `a published simple her stands for the original look`() {
        val card = her("simple-linche-v1", "simple")
        assertEquals(HerSelectability.SELECTABLE, herSelectability(card))
        assertEquals(StageChoice.Original, stageChoiceForHer(card))
    }

    @Test
    fun `a published live2d her stands for its engine`() {
        val card = her("live2d-linche-v2", "live2d")
        assertEquals(StageChoice.Engine(DhEngineId.LIVE2D), stageChoiceForHer(card))
    }

    @Test
    fun `a draft her cannot be chosen`() {
        val card = her("live2d-linche-v2", "live2d", status = "draft")
        assertEquals(HerSelectability.NOT_PUBLISHED, herSelectability(card))
        assertNull(stageChoiceForHer(card))
    }

    @Test
    fun `a renderer this client cannot drive cannot be chosen`() {
        // 服务端注册表已用 talkinghead，端上映射表还没有它：点了兑现不了，就不写选择。
        val card = her("talkinghead-linche-v1", "talkinghead")
        assertEquals(HerSelectability.RENDERER_UNSUPPORTED, herSelectability(card))
        assertNull(stageChoiceForHer(card))
    }

    // ---- 高亮：真源是 StageChoice，不是 her id ----

    @Test
    fun `with no user choice the server default is marked as followed`() {
        val hers = listOf(her("simple-linche-v1", "simple"), her("live2d-sudai-v1", "live2d"))
        val selection = herListSelection(StageChoice.Unset, roleId = "sudai", hers = hers)
        assertEquals("live2d-sudai-v1", selection.selectedHerId)
        assertEquals(true, selection.followingDefault)
    }

    @Test
    fun `the followed default is still marked even when this client cannot drive it`() {
        // 服务端默认是端上还接不了的 renderer：诚实标出「默认跟随」，卡片自己标「未接入」，
        // 舞台则按 resolveCallStage 回落到原来的形象。
        val card = her("talkinghead-linche-v1", "talkinghead")
        val selection = herListSelection(StageChoice.Unset, roleId = "linche", hers = listOf(card))
        assertEquals("talkinghead-linche-v1", selection.selectedHerId)
        assertEquals(true, selection.followingDefault)
    }

    @Test
    fun `with no user choice and no her nothing is marked`() {
        val selection = herListSelection(StageChoice.Unset, roleId = "linche", hers = emptyList())
        assertNull(selection.selectedHerId)
        assertEquals(false, selection.followingDefault)
    }

    @Test
    fun `an explicit engine choice marks the first card that yields it`() {
        // StageChoice 只存引擎不存 her id：两张 live2d 都能产生 LIVE2D 舞台，高亮第一张。
        val hers = listOf(her("live2d-linche-v2", "live2d"), her("live2d-linche-v3", "live2d"))
        val selection = herListSelection(
            StageChoice.Engine(DhEngineId.LIVE2D),
            roleId = "linche",
            hers = hers,
        )
        assertEquals("live2d-linche-v2", selection.selectedHerId)
        assertEquals(false, selection.followingDefault)
    }

    @Test
    fun `an explicit original-look choice marks the simple card`() {
        val hers = listOf(her("simple-linche-v1", "simple"), her("live2d-linche-v2", "live2d"))
        val selection = herListSelection(StageChoice.Original, roleId = "linche", hers = hers)
        assertEquals("simple-linche-v1", selection.selectedHerId)
        assertEquals(false, selection.followingDefault)
    }

    @Test
    fun `an explicit choice with no card behind it marks nothing`() {
        // 用户在通话页的引擎菜单里选过 VRM，但这个角色没有 VRM 的 her：列表不假装有选中。
        val hers = listOf(her("live2d-linche-v2", "live2d"))
        val selection = herListSelection(
            StageChoice.Engine(DhEngineId.VRM),
            roleId = "linche",
            hers = hers,
        )
        assertNull(selection.selectedHerId)
        assertEquals(false, selection.followingDefault)
    }

    @Test
    fun `a marked card is never one the user could not have chosen`() {
        // 草稿能进列表，但不能被标成「已选用」。
        val hers = listOf(her("live2d-linche-v2", "live2d", status = "draft"))
        val selection = herListSelection(
            StageChoice.Engine(DhEngineId.LIVE2D),
            roleId = "linche",
            hers = hers,
        )
        assertNull(selection.selectedHerId)
    }

    // ---- 文案：只报服务端真给过的值 ----

    @Test
    fun `status labels are the words the design asked for`() {
        assertEquals("已发布", herStatusLabel("published"))
        assertEquals("草稿", herStatusLabel("draft"))
        assertEquals("已下架", herStatusLabel("retired"))
        // 认不出的状态原样显示，不编一个「已发布」给用户。
        assertEquals("reviewing", herStatusLabel("reviewing"))
    }

    @Test
    fun `missing numbers hide their whole label`() {
        assertNull(herVersionLabel(her("simple-linche-v1", "simple", packVersion = 0)))
        assertEquals("pack v3", herVersionLabel(her("simple-linche-v1", "simple", packVersion = 3)))
        assertNull(herMetaLine(her("simple-linche-v1", "simple")))
        assertEquals("4 个特效 · 2 张贴纸", herMetaLine(
            her("live2d-linche-v2", "live2d", effectCount = 4, stickerCount = 2),
        ))
        assertEquals("3 张贴纸", herMetaLine(her("live2d-linche-v2", "live2d", stickerCount = 3)))
    }

    @Test
    fun `the card title falls back to the her id when the server sent no name`() {
        assertEquals("林澈·海边", herDisplayName(her("simple-linche-v1", "simple", name = "林澈·海边")))
        assertEquals("simple-linche-v1", herDisplayName(her("simple-linche-v1", "simple")))
    }
}
