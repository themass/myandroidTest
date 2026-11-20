package com.openapi.ks.myapp.companion

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
    }
}
