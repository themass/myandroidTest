package com.openapi.myapp.ui.base

import androidx.compose.ui.graphics.Color

/**
 * 暗色主题的统一颜色。所有 Compose 页面都从这里取，不再各自定义。
 * 夜间阅读要求：文字足够亮（主文字纯白，次级文字也保持高对比）。
 */
object ComposeTheme {
    /** 页面背景：比原来略亮，避免纯黑造成的压抑感。 */
    val Background = Color(0xFF23222C)
    /** 卡片 / 输入框背景。 */
    val Card = Color(0xFF302F3B)
    /** 分割线。 */
    val Divider = Color(0xFF4A4758)
    /** 主文字：纯白。 */
    val TextPrimary = Color(0xFFFFFFFF)
    /** 次级文字：提亮，保证小字也能看清。 */
    val TextSecondary = Color(0xFFD6D5E0)
    /** 强调色（按钮、链接）。 */
    val Accent = Color(0xFF8A6EFF)
    /** 已选中的 tab 背景。 */
    val Selected = Color(0xFF6D697D)
}
