package com.aimath.paizhao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    secondaryContainer = BlueSecondaryContainer,
    onSecondaryContainer = OnBlueSecondaryContainer,
    background = SurfaceSoft,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
)

@Composable
fun PaiZhaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = PaiZhaoTypography,
        content = content,
    )
}
