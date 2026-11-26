package com.openapi.feature.photosolve.markdown

import android.content.Context
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.feature.photosolve.SolveTypography
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
    // A42-#1：默认值也引用 token（步骤档）——之前这里 16.sp 是游离于四档之外的第五个事实源，
    // 页面调字号时默认路径不受控。调用点必须显式传 SolveTypography 的档位。
    fontSize: TextUnit = SolveTypography.step,
    selectable: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 公式字号必须跟 fontSize 走：之前 markwon 只按 context remember，公式永远按 16sp 渲染，
    // 于是「思考过程 15sp / 题目 20sp」这类设定里，公式和正文对不上，整页看起来忽大忽小。
    val markwon = remember(context, fontSize) { markwon(context, fontSize.value) }
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

private fun markwon(context: Context, textSizeSp: Float): Markwon {
    val app = context.applicationContext
    // 公式字号 = 正文字号 × SolveTypography.LATEX_SIZE_FACTOR：jlatexmath 的行内公式默认略小，
    // 但仍以调用方给的正文为基准；系数在 token 处统一调（A42-#1 把它从 1.15 降到 1.05）。
    val latexSize = textSizeSp.coerceAtLeast(10f) * app.resources.displayMetrics.scaledDensity * SolveTypography.LATEX_SIZE_FACTOR
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
