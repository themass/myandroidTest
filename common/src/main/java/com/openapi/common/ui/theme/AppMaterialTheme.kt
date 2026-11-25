package com.openapi.common.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * App 的 Material3 配色：全部从 [AppColors] 取，不再用 Material 默认的紫色。
 * 顶栏、底栏、侧边栏选中态、开关等组件的颜色都从这里来。
 */
val AppLightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    secondary = AppColors.Secondary,
    secondaryContainer = AppColors.ChatOutgoing,
    onSecondaryContainer = AppColors.ChatOutgoingText,
    surfaceVariant = AppColors.SurfaceVariant,
    surface = AppColors.Surface,
    background = AppColors.ChatBackground,
    error = AppColors.Error,
    outline = AppColors.Divider,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
)

/**
 * A38：全站字号的单一事实源。
 *
 * M3 默认 Typography 的 titleLarge=22sp、labelLarge=15sp，比我们的基线（title ≤18–20）大；
 * 以前这里不传 typography，所有 `MaterialTheme.typography.titleLarge` 调用点都吃默认 22sp。
 * 现在在主题层一次 pin 死，页面里不再逐个补 fontSize——要改字号改这里，不改页面。
 *
 * 值取自 [AppTypography]（screenTitle 18 / sectionTitle 16 / body 15 / caption 12）之上，
 * 只收超标的三档：titleLarge 22→20、titleMedium 16（未变，钉住防漂移）、labelLarge 15→14。
 */
val AppMaterialTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp),
    titleMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp),
)

/** App 的根 Compose 主题。页面根部用它代替裸的 [MaterialTheme]。 */
@Composable
fun AppMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppLightColorScheme, typography = AppMaterialTypography, content = content)
}
