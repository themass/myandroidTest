package com.openapi.feature.photosolve.markdown

import android.content.Context
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

/**
 * Markdown + LaTeX 渲染（Compose 里用）。
 *
 * 为什么先做纯文本、再升级成这个：题目、步骤、答案里全是公式，直接用 Compose `Text` 会显示
 * `$\frac{1}{2}+x^2$` 这样的源码；而 Compose 没有成熟的 LaTeX 渲染（`compose-richtext` 支持公式但不支持
 * 完整 Markdown）。这里的做法与 `:feature-chat` 的 `MarkdownText` 一致：Markwon 负责解析和排版，
 * 用 `AndroidView` 桥接进 Compose；LaTeX 由 `ext-latex`（jlatexmath-android）绘制。
 *
 * 与聊天的区别：**不加载网络图片**（题目解析里的图没有意义，还多一个 Glide 依赖），
 * 公式背景透明、颜色跟随调用方。
 */
@Composable
fun MathText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    selectable: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markwon = remember(context) { markwon(context) }
    val text = remember(markdown) { MathMarkdown.normalize(markdown) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(selectable)
                setLineSpacing(0f, 1.25f)
            }
        },
        update = { view ->
            if (color != Color.Unspecified) view.setTextColor(color.toArgb())
            view.textSize = fontSize.value
            markwon.setMarkdown(view, text)
        },
    )
}

/** 把一段文字包成 Drawable：jlatexmath 渲染失败时的兜底显示。 */
private fun textDrawable(context: Context, text: String): android.graphics.drawable.Drawable {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * context.resources.displayMetrics.scaledDensity
        color = 0xFFB00020.toInt()
    }
    val width = paint.measureText(text).toInt().coerceAtLeast(1)
    val height = (paint.descent() - paint.ascent()).toInt().coerceAtLeast(1)
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            canvas.drawText(text, 0f, -paint.ascent(), paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Drawable")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = width
        override fun getIntrinsicHeight(): Int = height
    }
}

private fun markwon(context: Context): Markwon {
    val app = context.applicationContext
    // 公式字号比正文稍大一点：jlatexmath 的行内公式默认偏小
    val latexSize = 16f * app.resources.displayMetrics.scaledDensity * 1.15f
    return Markwon.builder(app)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(SingleDollarLatexPlugin())
        .usePlugin(
            JLatexMathPlugin.create(latexSize) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
                // 公式出错时不要整段消失：把 LaTeX 源码画成文字，至少能看出内容
                builder.errorHandler { latex, _ -> textDrawable(app, latex) }
            },
        )
        .usePlugin(CorePlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(app))
        .build()
}
