package com.openapi.feature.video.subtitle.style

/**
 * 字幕的通用外观模型：颜色、底衬、字号、位置、行数、滚动方式。
 *
 * 这里刻意不 import 任何 Android/Compose 类型——颜色用打包好的 ARGB Int，字号用 sp 数值。
 * 这样这份模型能过 JVM 测试，将来服务端下发样式（WebVTT 的 STYLE 块也好、接口字段也好）
 * 也能直接落进同一形状，而不是每个页面自己拼一串显示用的字符串。
 * 放映场只是第一个用它的人。
 */
data class CaptionStyle(
    /** 文字颜色，0xAARRGGBB。 */
    val colorArgb: Int,
    /** 文字底衬的不透明度，0f = 无底衬。底衬永远是黑的，只有深浅可变——深色视频上这是可读性的底线。 */
    val backgroundAlpha: Float,
    /** 字号倍率，作用在 16sp 基准上。 */
    val fontScale: Float,
    val position: CaptionPosition,
    /** 一行最多几行；超出按 [motion] 处理。 */
    val maxLines: Int,
    val motion: CaptionMotionMode,
    /** 文字描边色 0xAARRGGBB；0 = 交给 [captionStrokeArgbOf] 兜底（无底衬一律补深色描边）。 */
    val strokeArgb: Int = 0,
) {
    companion object {
        /** 基准字号（sp）；[fontScale] 乘在它上面。 */
        const val BASE_TEXT_SP = 16f

        /** `#RGB` / `#RRGGBB` / `#AARRGGBB`，认不过就返回 null，由调用方决定回退。 */
        fun parseHexColor(raw: String?): Int? {
            val hex = raw?.trim()?.removePrefix("#") ?: return null
            val value = when (hex.length) {
                // 3 位先展开成 6 位，再和普通 6 位一样补全 alpha
                3 -> "FF" + buildString { hex.forEach { append(it).append(it) } }
                6 -> "FF$hex"
                8 -> hex
                else -> return null
            }
            val parsed = value.toLongOrNull(16) ?: return null
            return parsed.toInt()
        }
    }
}

/** 字幕贴在画面的哪一段。 */
enum class CaptionPosition { BOTTOM, MIDDLE, TOP }

/** 长句放不下的时候怎么办。 */
enum class CaptionMotionMode {
    /** 放得下就静止；单行超宽横向走字，多行超量自底向上滚。 */
    AUTO,
    /** 永远静止，超出的行直接裁掉。 */
    OFF,
    /** 无论放得下与否都滚动（单行走字、多行上滚）。 */
    SCROLL,
}

/** 兜底描边：和弹幕同一档深色（68% 黑），保证白字压在任何画面上都读得清。 */
internal const val FALLBACK_STROKE_ARGB = 0xB3000000.toInt()

/**
 * 最终描边色。底衬几乎透明时必须有描边——白字无描边压亮天空直接糊掉（「纯净」预设装机踩过）。
 * 显式描边优先；将来服务端下发样式缺这个字段，也走同一条兜底，不给不可读的样式放行。
 */
fun captionStrokeArgbOf(style: CaptionStyle): Int = when {
    style.strokeArgb != 0 -> style.strokeArgb
    style.backgroundAlpha < 0.1f -> FALLBACK_STROKE_ARGB
    else -> 0
}

/** 预设：面板里可点的那几张。样式是给用户选的成品，不是散开的旋钮。 */
data class CaptionPreset(val title: String, val hint: String, val style: CaptionStyle)

val captionPresets: List<CaptionPreset> = listOf(
    CaptionPreset(
        "默认", "白字黑底，最稳的看片设置",
        CaptionStyle(0xFFFFFFFF.toInt(), .78f, 1f, CaptionPosition.BOTTOM, 3, CaptionMotionMode.AUTO),
    ),
    CaptionPreset(
        "醒目", "黄字更大，适合台词密或画质糊的",
        CaptionStyle(0xFFFFE066.toInt(), .78f, 1.25f, CaptionPosition.BOTTOM, 3, CaptionMotionMode.AUTO),
    ),
    CaptionPreset(
        "纯净", "去掉底衬，白字描边，画面不挡",
        CaptionStyle(0xFFFFFFFF.toInt(), 0f, 1f, CaptionPosition.BOTTOM, 3, CaptionMotionMode.AUTO, FALLBACK_STROKE_ARGB),
    ),
    CaptionPreset(
        "顶部", "字幕贴上面，不挡演员脸",
        CaptionStyle(0xFFFFFFFF.toInt(), .78f, 1f, CaptionPosition.TOP, 2, CaptionMotionMode.AUTO),
    ),
    CaptionPreset(
        "大字走字", "一行到底，长句横向滚出来",
        CaptionStyle(0xFFFFFFFF.toInt(), .78f, 1.25f, CaptionPosition.BOTTOM, 1, CaptionMotionMode.SCROLL),
    ),
)

/** 预设存取：SharedPreferences 里一个 int（序号），坏了回退合法范围。 */
fun captionPresetIndex(raw: Int): Int = raw.coerceIn(0, captionPresets.lastIndex)
