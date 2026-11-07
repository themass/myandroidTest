package com.openapi.ks.auxio.musicui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuxioDark = darkColorScheme(
    primary = Color(0xFF8FAEFF),
    onPrimary = Color(0xFF0B1020),
    secondary = Color(0xFF5C6BC0),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF181C22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFFB8BEC9),
)

private val AuxioLight = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF1A1C20),
    surface = Color.White,
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE4E6EB),
    onSurfaceVariant = Color(0xFF4A4F59),
)

@Composable
fun AuxioMusicTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors: ColorScheme = if (dark) AuxioDark else AuxioLight
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
