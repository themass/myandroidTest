package com.openapi.feature.video.screening

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A38 第 1 批：放映场字号 token 的层级钉（台账 docs/design/20261125-a38-font-wrap-audit.md）。
 *
 * 该模块历史上把 17–37sp 散在页面上，谁改一个页面就漂一个。现在字号只有这一个来源，
 * 这里钉住层级与上限：改 token 值必须同时改这条测试和台账，防止无声回涨。
 */
class ScreeningTypographyTest {

    @Test
    fun `token values match the A38 audit ladder`() {
        assertEquals(24.sp, ScreeningTypography.hero)
        assertEquals(22.sp, ScreeningTypography.featureTitle)
        assertEquals(18.sp, ScreeningTypography.page)
        assertEquals(17.sp, ScreeningTypography.item)
        assertEquals(16.sp, ScreeningTypography.section)
        assertEquals(20.sp, ScreeningTypography.overlay)
    }

    @Test
    fun `no persistent tier exceeds the 20sp title ceiling`() {
        // 审计基线：title ≤18–20sp。hero/featureTitle 是海报级展示字，允许到 24/22，
        // 其余层级一旦越过 20sp 就是回潮。
        assertTrue(ScreeningTypography.page <= 20.sp)
        assertTrue(ScreeningTypography.item <= 20.sp)
        assertTrue(ScreeningTypography.section <= 20.sp)
    }

    @Test
    fun `hierarchy stays strictly ordered`() {
        assertTrue(ScreeningTypography.section < ScreeningTypography.item)
        assertTrue(ScreeningTypography.item < ScreeningTypography.page)
        assertTrue(ScreeningTypography.page < ScreeningTypography.featureTitle)
        assertTrue(ScreeningTypography.featureTitle <= ScreeningTypography.hero)
    }

    @Test
    fun `line heights stay below 1_5x font size`() {
        // 行高超 1.5 倍字号就是旧 hero（37/42 除外）留下的占地方毛病；审计收小后钉住。
        assertTrue(
            ScreeningTypography.heroLineHeight.value < ScreeningTypography.hero.value * 1.45f,
        )
        assertTrue(
            ScreeningTypography.featureTitleLineHeight.value < ScreeningTypography.featureTitle.value * 1.45f,
        )
        assertTrue(
            ScreeningTypography.pageLineHeight.value < ScreeningTypography.page.value * 1.45f,
        )
        assertTrue(
            ScreeningTypography.itemLineHeight.value < ScreeningTypography.item.value * 1.45f,
        )
    }
}
