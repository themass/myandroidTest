package com.openapi.ks.myapp.companion

/**
 * persona 着陆页（OpenDesign 稿 01「她是谁」）的纯决策层。与 her 列表页
 * （[HerListChoice]）同一分层：UI 只画这里给的结果，把点击转成一次导航/选择，
 * 不自己判「该显示什么」。
 *
 * 三条口径：
 * 1. **屏上有什么由数据决定**：已经拉到的列表比一次刷新失败更值得留着（与角色列表页
 *    既有口径一致）；只有空屏才谈加载 / 错误 / 登录；
 * 2. **未登录与加载失败分开**：判据是 token 在不在，不是错误文案——没有 token 时
 *    列表必然 401，把「请先登录」报成「加载失败」是让用户去重试一件重试不了的事；
 * 3. **文案只报服务端真给过的东西**：声音标签没下发就不画 pill，名字缺失退回 id
 *    （与 [herVersionLabel] 等同一条纪律，不编「VOICE 03」这种示意值）。
 */

/** 着陆页当前该画哪一块。优先级在 [personaLandingContent] 里，只有一份。 */
enum class PersonaLandingContent {
    /** 双列卡格（稿 01 的 .grid）。 */
    GRID,

    /** 空屏 + 还在拉：转圈。 */
    LOADING,

    /** 没有登录 token：文案 + 「去登录」，重试没有意义。 */
    NEEDS_LOGIN,

    /** 有 token 但拉失败了：错误 + 重试。 */
    ERROR,

    /** 已登录、没报错、但服务端一个角色都没有：诚实的空。 */
    EMPTY,
}

/**
 * 空屏时的四种态怎么分、有货时一律留货。顺序就是优先级：
 * 旧列表 > 加载中 > 未登录 > 加载失败 > 真空。
 */
fun personaLandingContent(
    loading: Boolean,
    error: String?,
    itemCount: Int,
    hasToken: Boolean,
): PersonaLandingContent = when {
    itemCount > 0 -> PersonaLandingContent.GRID
    loading -> PersonaLandingContent.LOADING
    !hasToken -> PersonaLandingContent.NEEDS_LOGIN
    error != null -> PersonaLandingContent.ERROR
    else -> PersonaLandingContent.EMPTY
}

// ---- 卡片文案 ----

/** 名字：服务端给的就用（trim 后），没给退回 id——可见的原始标识，空白卡比 id 更糟。 */
fun personaDisplayName(character: CharacterSummary): String =
    character.name.trim().takeIf { it.isNotEmpty() } ?: character.id

/**
 * 声音 pill：不能语音就说「暂时不能语音」（能力缺席比名字重要）；
 * 能语音但服务端没发标签 → null，不画 pill（稿上的「VOICE 03」是示意值，不照抄）。
 */
fun personaVoicePill(character: CharacterSummary): String? {
    if (!character.voiceAvailable) return "暂时不能语音"
    return character.voiceLabel?.trim()?.takeIf { it.isNotEmpty() }
}

/** 人设标签行：空白项丢掉，剩下的用「·」连；一个都没有就不显示（null）。 */
fun personaTagsLine(character: CharacterSummary): String? =
    character.tags.map { it.trim() }.filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** 角标：只有当前正在聊的角色带「使用中」；其余卡不带角标。 */
fun personaCardBadge(isCurrent: Boolean): String? = if (isCurrent) "使用中" else null

// ---- 导航：首屏落点与回退 ----

/**
 * 陪伴 tab 的首屏。两种情形直达 CHAT 绕过着陆页：
 * - 聊天页电话入口（[launchCallOnOpen]）：用户要的是立刻通话，先卡一张选人格页是挡路；
 * - adb 剧本房（[scriptCaseId] 非空白）：自动化要直达会话现场。
 * 其余情形落在 PERSONAS（稿 01 的本义：先选一个想陪你说话的人）。
 */
fun companionLandingPage(launchCallOnOpen: Boolean, scriptCaseId: String?): CompanionPage =
    if (launchCallOnOpen || !scriptCaseId.isNullOrBlank()) CompanionPage.CHAT else CompanionPage.PERSONAS

/**
 * 子页面返回键的去向。HERS 取决于从哪进来（着陆页的「呈现形象」→ 回着陆页；
 * 聊天页菜单 → 回聊天），其余子页面一律回 CHAT——与既有「子页面只是 page 状态切换」
 * 的状态机一致，不另起返回栈。
 */
fun backTargetFor(page: CompanionPage, herBack: CompanionPage): CompanionPage =
    if (page == CompanionPage.HERS) herBack else CompanionPage.CHAT
