package com.openapi.chat.presentation.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin

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
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor.toArgb())
                TextViewCompat.setTextAppearance(
                    this,
                    androidx.appcompat.R.style.TextAppearance_AppCompat_Medium,
                )
            }
        },
        update = { view ->
            val markwon = Markwon.builder(view.context)
                .usePlugin(LinkifyPlugin.create())
                .build()
            markwon.setMarkdown(view, markdown)
        },
    )
}
