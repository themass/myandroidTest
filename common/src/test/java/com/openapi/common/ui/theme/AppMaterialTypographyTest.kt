package com.openapi.common.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A38 第 1 批：主题层字号 pin（台账 docs/design/20261125-a38-font-wrap-audit.md 根因①）。
 *
 * M3 默认 Typography 的 titleLarge=22sp 超过全站基线（title ≤18–20sp），
 * 所有 `MaterialTheme.typography.titleLarge` 调用点都跟着超。修法是在 AppMaterialTheme
 * 一次 pin，而不是在页面里逐个补 fontSize——这里钉住 pin 值，防止主题被改回裸默认。
 */
class AppMaterialTypographyTest {

    @Test
    fun `title tiers stay within the audit baseline`() {
        assertEquals(20.sp, AppMaterialTypography.titleLarge.fontSize)
        assertEquals(16.sp, AppMaterialTypography.titleMedium.fontSize)
        assertEquals(14.sp, AppMaterialTypography.labelLarge.fontSize)
    }
}
