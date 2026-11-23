package com.openapi.feature.chat.presentation.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import com.openapi.feature.chat.markdown.ChatMarkwon
import com.openapi.feature.chat.markdown.MarkdownRenderCache

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
    // 每个气泡一份分段缓存（整改清单 P1-12）：流式吐字时每拍只重新解析还在长的最后一段，
    // 已完成的顶层块复用；文本没变的重组（滚动、兄弟消息刷新）直接拿回同一份结果。
    val cache = remember { MarkdownRenderCache() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                // 不抢长按：原生 TextView 的长按会被当成「选字」吞掉事件，长按消息菜单就打不开。
                // 关掉长按与点击，让按压交给外层的消息气泡（链接仍可点，见 ChatMarkwon 的链接设置）。
                isLongClickable = false
                isClickable = false
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
            val spanned = cache.render(ChatMarkwon.get(view.context), markdown)
            // setParsedMarkdown 和 setMarkdown 一样会过所有插件的 before/afterSetText
            // （图片调度、LaTeX 都挂在上面），只是跳过重复的 Markdown 解析。
            // 缓存命中的重组里 TextView 里已经是这份内容：连 setText 都省掉，不重新排版。
            if (view.text !== spanned) ChatMarkwon.get(view.context).setParsedMarkdown(view, spanned)
            // Markwon 会给带链接的文字挂上 LinkMovementMethod，它会吞掉按压，导致外层长按菜单打不开。
            // 只有真的有链接时才保留它；没有链接的文字不挂，让按压交给气泡。
            val hasLinks = (view.text as? android.text.Spanned)
                ?.getSpans(0, view.text.length, android.text.style.URLSpan::class.java)
                ?.isNotEmpty() == true
            view.movementMethod = if (hasLinks) android.text.method.LinkMovementMethod.getInstance() else null
        },
    )
}
