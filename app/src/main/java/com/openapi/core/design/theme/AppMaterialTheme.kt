package com.openapi.core.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/** App 的根 Compose 主题。页面根部用它代替裸的 [MaterialTheme]。 */
@Composable
fun AppMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppLightColorScheme, content = content)
}
