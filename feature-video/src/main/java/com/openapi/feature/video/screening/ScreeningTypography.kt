package com.openapi.feature.video.screening

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A38：放映场（深色改版）字号的单一事实源。
 *
 * 该模块以前把 17–37sp 硬编码在页面上（全站 ≥17sp 的 51 处里占 25 处），改一处忘一处。
 * 现在按层级收进这里，页面上只引用 token；要调字号改本文件，不改页面。
 * 值来自 docs/design/20261125-a38-font-wrap-audit.md 的建议列（基线：title ≤18–20）：
 * 页标 18 / 区块 16 / 卡标 22 / hero 24（24–26 取低值，深色大留白压得住）。
 */
object ScreeningTypography {
    /** 首屏 hero 句（"戴上耳机，世界慢下来。"等）。 */
    val hero: TextUnit = 24.sp
    val heroLineHeight: TextUnit = 32.sp

    /** 大卡片名：图集精选卡、播放详情页标题。审计前 34–35sp，收一档。 */
    val featureTitle: TextUnit = 22.sp
    val featureTitleLineHeight: TextUnit = 27.sp

    /** 页面级标题：FeedShell 顶栏、热映卡片名、频道页标题。 */
    val page: TextUnit = 18.sp
    val pageLineHeight: TextUnit = 23.sp

    /** 列表项/半屏顶栏标题：审计判临界保留，钉住防漂移。 */
    val item: TextUnit = 17.sp
    val itemLineHeight: TextUnit = 22.sp

    /** 区块标题：Heading()、「子频道/全部视频」、设置面板当前值。 */
    val section: TextUnit = 16.sp

    /** 瞬时 overlay（快进 "+5s"）：一闪即走，不占版面，保留大字。 */
    val overlay: TextUnit = 20.sp
}
