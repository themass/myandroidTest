package com.openapi.chat.presentation.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import chat.ui.markdown.ChatMarkwon
import chat.ui.markdown.FormulaInputNormalizer

/**
 * Markdown 渲染。
 *
 * 选择 Markwon（View 方案 + AndroidView 桥接）而不是纯 Compose 渲染库：
 * - Markwon 是这条赛道里最成熟、维护最稳定的
 * - 终端样式（代码块、列表、链接）已经打磨到位，不需要自己实现
 * - 与现有 Compose 页面共存成本很低，一个 AndroidView 即可
 *
 * 只用于 assistant 消息：用户自己输入的内容按纯文本显示更符合预期。
 */
@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color = Color.Black,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 16f,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                // 先套文字外观，再设颜色和字号：反过来的话外观会把颜色覆盖成主题灰色
                TextViewCompat.setTextAppearance(
                    this,
                    androidx.appcompat.R.style.TextAppearance_AppCompat_Medium,
                )
                setTextColor(textColor.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setLineSpacing(0f, 1.2f)
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            ChatMarkwon.get(view.context).setMarkdown(view, FormulaInputNormalizer.forRender(markdown))
        },
    )
}
