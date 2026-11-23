package com.openapi.feature.chat.presentation.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import com.openapi.feature.chat.markdown.ChatMarkwon
import com.openapi.feature.chat.markdown.FormulaInputNormalizer
import com.openapi.feature.chat.markdown.MarkdownStableTail
import io.noties.markwon.Markwon

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
                tag = MarkdownRenderCache()
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            val normalized = FormulaInputNormalizer.forRender(markdown)
            val split = MarkdownStableTail.split(normalized)
            val markwon = ChatMarkwon.get(view.context)
            val cache = view.tag as MarkdownRenderCache
            setMarkdownIncremental(view, markwon, cache, split)
            val hasLinks = (view.text as? Spanned)
                ?.getSpans(0, view.text.length, URLSpan::class.java)
                ?.isNotEmpty() == true
            view.movementMethod = if (hasLinks) android.text.method.LinkMovementMethod.getInstance() else null
        },
    )
}

private data class MarkdownRenderCache(
    var stableSource: String? = null,
    var stableSpanned: Spanned? = null,
)

/**
 * 已闭合段落缓存在 View tag 上，流式时只重解析 [MarkdownStableTail.Split.tail]（P1-12）。
 */
private fun setMarkdownIncremental(
    view: TextView,
    markwon: Markwon,
    cache: MarkdownRenderCache,
    split: MarkdownStableTail.Split,
) {
    val builder = SpannableStringBuilder()
    if (split.stable.isNotEmpty()) {
        val stableKey = split.stable
        if (cache.stableSource != stableKey) {
            cache.stableSource = stableKey
            cache.stableSpanned = markwon.toMarkdown(stableKey.trimEnd())
        }
        cache.stableSpanned?.let { builder.append(it) }
    } else {
        cache.stableSource = ""
        cache.stableSpanned = null
    }
    if (split.tail.isNotEmpty()) {
        builder.append(markwon.toMarkdown(split.tail))
    }
    if (builder.isEmpty() && split.stable.isEmpty() && split.tail.isEmpty()) {
        view.text = ""
    } else {
        view.text = builder
    }
}
