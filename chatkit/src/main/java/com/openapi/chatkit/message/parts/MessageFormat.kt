package com.openapi.chatkit.message.parts

import java.util.Locale

/**
 * 秒 → 「m:ss」或「h:mm:ss」（视频卡片与草稿角标共用）。
 *
 * 单独放在这个纯 Kotlin 文件里而不是 MessageParts.kt：那个文件整篇都是 Compose，
 * 格式化规则却要在 JVM 单测里直接断言（chatkit 的测试没有 Robolectric），
 * 纯函数不该被同文件里的 UI 代码连累出测试环境。
 * 负数按 0 处理：显示 -1:00 的气泡比不显示更糟。
 */
fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
