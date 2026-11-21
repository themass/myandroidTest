package com.openapi.ks.myapp.companion

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2

/**
 * 通话页的背景主题（纯数据，绘制方在 CompanionCallScreen 消费）。
 *
 * 只管「页面氛围」那一层：渐变背景、光晕、强调色。人物画面里的背景是素材烤进
 * 模型的（Duix 视频底），不归主题管，也不该由客户端硬换。
 *
 * [Default] 的每个色值都必须等于旧的 `CallColors` 常量——主题是新加的可选项，
 * 没设置过的用户看到的画面必须和以前逐像素一致（「不要改原来的UI」）。
 */
@Immutable
data class CallTheme(
    val id: String,
    /** 下拉里给用户看的名字。 */
    val uiName: String,
    val topArgb: Int,
    val midArgb: Int,
    val bottomArgb: Int,
    /** 主光晕的基色。 */
    val glowArgb: Int,
    /** 光晕强度系数（默认主题的旧版设计值是 0.34；OLED 主题更收敛）。 */
    val glowStrength: Float,
    /** 底部托光（把操作条从背景里托起来）。 */
    val bottomGlowArgb: Int,
    /** 强调色：状态点、时长等「她在线」的信号跟着主题走。 */
    val accentArgb: Int,
    /** 自定义主题的色相（0..360，调色弹窗滑杆回填用）；预设主题为 null。 */
    val hueDegrees: Float? = null,
) {
    val top get() = Color(topArgb)
    val mid get() = Color(midArgb)
    val bottom get() = Color(bottomArgb)
    val glow get() = Color(glowArgb)
    val bottomGlow get() = Color(bottomGlowArgb)
    val accent get() = Color(accentArgb)

    companion object {
        /** 默认主题 = 原来的深海配色，必须与 CallColors 常量保持一致（测试钉住）。 */
        val Default = CallTheme(
            id = "deep_sea", uiName = "深海",
            topArgb = 0xFF161F3D.toInt(), midArgb = 0xFF0B1122.toInt(), bottomArgb = 0xFF05060C.toInt(),
            glowArgb = 0xFF5B4BC4.toInt(), glowStrength = 0.34f,
            bottomGlowArgb = 0xFF2A3A6B.toInt(), accentArgb = 0xFF8B7CF6.toInt(),
        )

        /** 预设表；第一项是默认。 */
        val Presets = listOf(
            Default,
            CallTheme(
                id = "warm_night", uiName = "暖夜",
                topArgb = 0xFF241A14.toInt(), midArgb = 0xFF120B08.toInt(), bottomArgb = 0xFF070505.toInt(),
                glowArgb = 0xFF8A5A28.toInt(), glowStrength = 0.30f,
                bottomGlowArgb = 0xFF6B4A2A.toInt(), accentArgb = 0xFFF0B67F.toInt(),
            ),
            CallTheme(
                // OLED：渐变全黑省电，光晕收敛到若隐若现，避免纯黑上糊一圈紫。
                id = "oled", uiName = "纯黑",
                topArgb = 0x000000, midArgb = 0x000000, bottomArgb = 0x000000,
                glowArgb = 0xFF453A94.toInt(), glowStrength = 0.20f,
                bottomGlowArgb = 0xFF1B2547.toInt(), accentArgb = 0xFF9D8FFF.toInt(),
            ),
        )

        /** 按存储 id 解析；空/未知一律回落默认（值可能是旧版本写的）。 */
        fun fromName(name: String?): CallTheme =
            Presets.firstOrNull { it.id == name } ?: Default

        /** 自定义主题的存储 id（色相另存在 hue 键里，见 CallThemePreferences）。 */
        const val CUSTOM_ID = "custom"

        /** 自定义标记在但色相丢失（旧版本写过坏值）时的兜底色相：深海同款紫。 */
        const val DEFAULT_CUSTOM_HUE = 248f
    }
}

/**
 * 由一个色相派生整套自定义主题：用户只转一个色轮，暗部明度、光晕强度这些
 * 调不好的参数由我们钉死。派生规则对着深海/暖夜的观感来：
 * 背景是同色相的近黑三段渐变（顶最亮、逐级变暗），光晕/强调色是同色相的高饱和亮色。
 */
fun customCallTheme(hueDegrees: Float): CallTheme {
    val hue = ((hueDegrees % 360f) + 360f) % 360f
    return CallTheme(
        id = CallTheme.CUSTOM_ID,
        uiName = "自定义",
        topArgb = hslToArgb(hue, 0.45f, 0.16f),
        midArgb = hslToArgb(hue, 0.50f, 0.085f),
        bottomArgb = hslToArgb(hue, 0.55f, 0.035f),
        glowArgb = hslToArgb(hue, 0.68f, 0.52f),
        glowStrength = 0.32f,
        bottomGlowArgb = hslToArgb(hue, 0.45f, 0.29f),
        accentArgb = hslToArgb(hue, 0.80f, 0.74f),
        hueDegrees = hue,
    )
}

/**
 * 色环取色：把触点 (x, y) 换算成色相角（度，顺时针，右=0° 下=90°）。
 * 屏幕坐标 y 向下，所以直接用 atan2(dy, dx) 就是「顺时针为正」，映射到 0..360。
 */
fun hueFromPoint(x: Float, y: Float, centerX: Float, centerY: Float): Float {
    val raw = atan2(y - centerY, x - centerX) * (180.0 / PI).toFloat()
    return ((raw % 360f) + 360f) % 360f
}

/** HSL → sRGB（ARGB 不透明）。android.graphics.Color.HSLToColor 不在这层的测试依赖里，自己算。 */
private fun hslToArgb(h: Float, s: Float, l: Float): Int {
    fun channel(n: Int): Float {
        val k = (n + h / 30f) % 12f
        val a = s * minOf(l, 1f - l)
        return l - a * maxOf(-1f, minOf(k - 3f, 9f - k, 1f))
    }
    fun byte(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (byte(channel(0)) shl 16) or (byte(channel(8)) shl 8) or byte(channel(4))
}
