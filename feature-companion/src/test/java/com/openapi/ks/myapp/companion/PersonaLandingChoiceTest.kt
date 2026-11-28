package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * persona 着陆页（OpenDesign 稿 01「她是谁」）的纯决策。钉四条口径：
 *
 * 1. **屏上有什么由数据决定，不由「请求成功了吗」决定**：已经拉到的列表比一次刷新失败
 *    更值得留着（与角色列表页既有口径一致）；只有空屏才谈加载/错误/登录。
 * 2. **未登录与加载失败分开**：没有 token 时列表必然 401，把「请先登录」报成「加载失败」
 *    是让用户去重试一件重试不了的事。判据用 token 在不在，不去匹配错误文案。
 * 3. **卡片文案只报服务端真给过的东西**：声音标签没下发就不画 pill（与 her 列表页同一纪律）；
 *    名字缺失退回 id，绝不出现空白卡。
 * 4. **首屏落点与回退是导航状态，不是散在各处的 if**：adb 剧本房 / 聊天页跳进通话都必须
 *    绕过着陆页直达 CHAT；HERS 的返回目标取决于从哪进来（着陆页 → 回着陆页，聊天 → 回聊天）。
 */
class PersonaLandingChoiceTest {

    private fun character(
        id: String,
        name: String = id,
        tagline: String = "",
        tags: List<String> = emptyList(),
        voiceAvailable: Boolean = true,
        voiceLabel: String? = null,
    ) = CharacterSummary(
        id = id,
        name = name,
        tagline = tagline,
        tags = tags,
        voiceAvailable = voiceAvailable,
        voiceLabel = voiceLabel,
    )

    // ---- 1. 屏上该显示什么 ----

    @Test
    fun `a list already on screen survives a failed refresh`() {
        assertEquals(
            PersonaLandingContent.GRID,
            personaLandingContent(loading = false, error = "网络中断", itemCount = 3, hasToken = true),
        )
        // 刷新中的旧列表同样留着，不闪回加载态。
        assertEquals(
            PersonaLandingContent.GRID,
            personaLandingContent(loading = true, error = null, itemCount = 1, hasToken = true),
        )
    }

    @Test
    fun `loading only while the screen is empty`() {
        assertEquals(
            PersonaLandingContent.LOADING,
            personaLandingContent(loading = true, error = null, itemCount = 0, hasToken = true),
        )
    }

    @Test
    fun `no token is a login prompt, not a generic load failure`() {
        // 没有 token 时服务端必然回 401；让用户点「重试」是骗他。
        assertEquals(
            PersonaLandingContent.NEEDS_LOGIN,
            personaLandingContent(loading = false, error = "登录已失效，请重新登录", itemCount = 0, hasToken = false),
        )
        // 文案缺席也一样：判据是 token，不是错误字符串。
        assertEquals(
            PersonaLandingContent.NEEDS_LOGIN,
            personaLandingContent(loading = false, error = null, itemCount = 0, hasToken = false),
        )
    }

    @Test
    fun `a failed fetch with a token is retryable`() {
        assertEquals(
            PersonaLandingContent.ERROR,
            personaLandingContent(loading = false, error = "加载失败", itemCount = 0, hasToken = true),
        )
    }

    @Test
    fun `a token and no error but nobody up is an honest empty`() {
        assertEquals(
            PersonaLandingContent.EMPTY,
            personaLandingContent(loading = false, error = null, itemCount = 0, hasToken = true),
        )
    }

    // ---- 2. 卡片文案 ----

    @Test
    fun `the voice pill shows only a label the server really sent`() {
        assertEquals(
            "沉稳男声",
            personaVoicePill(character("linche", voiceLabel = "沉稳男声")),
        )
        // 没下发标签就不画 pill：编一个「VOICE 03」比没有更糟（稿子里那个编号是示意值）。
        assertNull(personaVoicePill(character("linche")))
        assertNull(personaVoicePill(character("linche", voiceLabel = "   ")))
    }

    @Test
    fun `a character without voice says so instead of a label`() {
        assertEquals(
            "暂时不能语音",
            personaVoicePill(character("linche", voiceAvailable = false, voiceLabel = "沉稳男声")),
        )
    }

    @Test
    fun `tags join with separators and drop blanks`() {
        assertEquals("安静 · 治愈", personaTagsLine(character("a", tags = listOf("安静", "", "  ", "治愈"))))
        assertNull(personaTagsLine(character("a", tags = emptyList())))
        assertNull(personaTagsLine(character("a", tags = listOf("  "))))
    }

    @Test
    fun `a blank name falls back to the id, never an empty card`() {
        assertEquals("linche", personaDisplayName(character("linche", name = "  ")))
        assertEquals("林澈", personaDisplayName(character("linche", name = " 林澈 ")))
    }

    @Test
    fun `only the character in use gets the corner badge`() {
        assertEquals("使用中", personaCardBadge(isCurrent = true))
        assertNull(personaCardBadge(isCurrent = false))
    }

    // ---- 3. 首屏落点与回退 ----

    @Test
    fun `the companion tab lands on the persona grid`() {
        assertEquals(
            CompanionPage.PERSONAS,
            companionLandingPage(launchCallOnOpen = false, scriptCaseId = null),
        )
    }

    @Test
    fun `call entry and script rooms bypass the landing`() {
        // 聊天页电话入口：进来就要进通话，先卡一张选人格的页是挡路。
        assertEquals(
            CompanionPage.CHAT,
            companionLandingPage(launchCallOnOpen = true, scriptCaseId = null),
        )
        // adb 剧本房（--es script_case）：自动化要直达会话现场。
        assertEquals(
            CompanionPage.CHAT,
            companionLandingPage(launchCallOnOpen = false, scriptCaseId = "case-7"),
        )
        assertEquals(
            CompanionPage.PERSONAS,
            companionLandingPage(launchCallOnOpen = false, scriptCaseId = "   "),
        )
    }

    @Test
    fun `HERS returns to wherever it was opened from`() {
        // 从着陆页的「呈现形象」进来 → 回着陆页；从聊天页菜单进来 → 回聊天。
        assertEquals(CompanionPage.PERSONAS, backTargetFor(CompanionPage.HERS, herBack = CompanionPage.PERSONAS))
        assertEquals(CompanionPage.CHAT, backTargetFor(CompanionPage.HERS, herBack = CompanionPage.CHAT))
    }

    @Test
    fun `every other subpage backs to the chat page`() {
        for (page in listOf(CompanionPage.PERSONAS, CompanionPage.MEMORY, CompanionPage.SETTINGS)) {
            assertEquals(CompanionPage.CHAT, backTargetFor(page, herBack = CompanionPage.PERSONAS))
        }
    }
}
