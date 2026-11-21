package com.openapi.mediaview.edit

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * 一张 3D 颜色查找表（LUT）。
 *
 * [rgb] 是 [size]³ 个采样点的 RGB 分量展平后的数组，长度 `size³ * 3`，值域 0..1，
 * 排列顺序与 `.cube` 文本一致：**r 变化最快**，其次 g，最慢 b
 * （即第 (r,g,b) 个采样点的分量在 `((b * size + g) * size + r) * 3`）。
 *
 * 只有数据、不碰 Android，位图相关的事都在 [LutApply] 里，这样「表怎么读进来」和
 * 「表怎么作用到位图上」互不牵连（渲染器只需要一个 `(String) -> Lut3D?`）。
 */
data class Lut3D(val size: Int, val rgb: FloatArray)

/**
 * 解析 `.cube`（Adobe / DaVinci Resolve 通用文本格式）。
 *
 * 认识 `LUT_3D_SIZE`（必需）、`LUT_ORDER`（只接受 RGB）、`TITLE`、`DOMAIN_MIN` / `DOMAIN_MAX`、
 * `#` 注释与空行；其余行按「恰好三个 float」收进表里。
 *
 * 任何不合预期都返回 null 而不是抛异常——滤镜文件既可能来自资产目录也可能来自用户导入，
 * 一个坏文件不该把编辑器带崩（调用方拿到 null 就当作「没装滤镜」）。
 */
object CubeLut {

    /** 上界防呆：真实滤镜表一般是 16³/33³/64³，超过这个数基本可判定文件坏了（也顺手防止超大表撑爆内存）。 */
    private const val MAX_SIZE = 64

    /** 只处理 3D 表；出现这些关键字就说明文件不是我们要的形状（一维表 / 带 shaper 的 Resolve 表）。 */
    private val UNSUPPORTED = setOf("LUT_1D_SIZE", "SHAPER")

    /** 真实文件里既可能是空格也可能是制表符对齐。 */
    private val WHITESPACE = Regex("""\s+""")

    /**
     * @return 解析出的表；文本不合预期时返回 null
     */
    fun parse(text: String): Lut3D? {
        var size = -1
        // 数值行有可能排在 LUT_3D_SIZE 之前，所以先攒在可增长的数组里，最后再核对数量
        var values = FloatArray(1024)
        var count = 0

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // 关键字和它后面的值之间可能是空格也可能是制表符
            val space = WHITESPACE.find(line)?.range?.first ?: line.length
            val keyword = line.substring(0, space)
            val rest = line.substring(space).trim()

            when {
                keyword.equals("LUT_3D_SIZE", ignoreCase = true) -> {
                    if (size >= 0) return null // 出现两次表头，文件不可信
                    size = rest.toIntOrNull() ?: return null
                    if (size < 2 || size > MAX_SIZE) return null
                }
                // 只支持 RGB 顺序：BGR 之类的表当成 RGB 采样会直接偏色
                keyword.equals("LUT_ORDER", ignoreCase = true) ->
                    if (!rest.equals("RGB", ignoreCase = true)) return null

                keyword.equals("TITLE", ignoreCase = true) -> Unit
                keyword.equals("DOMAIN_MIN", ignoreCase = true) ||
                    keyword.equals("DOMAIN_MAX", ignoreCase = true) -> Unit // 采样按 0..1，忽略

                keyword.uppercase() in UNSUPPORTED -> return null

                // 表头之前的怪东西（未加引号的标题之类）忽略；表头之后必须老老实实是数值行
                size < 0 -> Unit

                else -> {
                    // 真实文件里数字之间可能是连续空格或制表符对齐，所以按空白串切
                    val parts = line.split(WHITESPACE)
                    if (parts.size != 3) return null
                    // 表尺寸已知，多出来的数值直接判坏，不让缓冲继续长下去
                    val expected = size * size * size * 3
                    if (count + 3 > expected) return null
                    for (p in parts) {
                        val v = p.toFloatOrNull() ?: return null
                        if (count == values.size) values = values.copyOf(minOf(values.size * 2, expected))
                        values[count++] = v
                    }
                }
            }
        }

        if (size < 2) return null
        val expected = size * size * size * 3
        if (count != expected) return null

        val rgb = FloatArray(expected) { values[it].coerceIn(0f, 1f) }
        return Lut3D(size, rgb)
    }
}

/**
 * 把 [Lut3D] 套到位图上：逐像素三线性插值，再按强度与原图混合。
 *
 * 强度合成只允许这一个公式（逐通道）：
 *
 * ```
 * out = orig + (lut(orig) - orig) * intensity / 100
 * ```
 *
 * 于是 0 是「完全原图」，100 是「完全套表」，中间线性过渡，滑杆手感可预期。
 * 每个像素 O(1)：一次取 8 个角点做插值，640×480 约 30 万点，纯 Kotlin 循环够用。
 */
object LutApply {

    private const val ALPHA_MASK = -0x1000000 // 0xFF000000

    /**
     * **就地**修改 [target]（必须可变；解码出来的位图通常不可变，先 `copy(config, true)`），并返回它本身。
     * 不新建位图：预览每拖一下强度滑杆都要跑一遍，多分配一张大图代价太大。
     *
     * 就地改**不可叠加**：同一个 target 连跑两次等于把表套了两遍（第二次是在已经套过表的像素上再套），
     * 结果和跑一次不一样。所以调用方每次都要从干净的副本起，不要在已套过表的位图上重复调用。
     *
     * @param intensity 0..100，越界会被夹到边界
     */
    fun apply(target: Bitmap, lut: Lut3D, intensity: Int): Bitmap {
        val t = intensity.coerceIn(0, 100) / 100f
        if (t == 0f) return target

        val w = target.width
        val h = target.height
        val px = IntArray(w * h)
        target.getPixels(px, 0, w, 0, 0, w, h)

        val size = lut.size
        val last = size - 1
        val rgb = lut.rgb
        // 255 → size-1 的映射，顺带算出沿 g / b 走一格的索引步长
        val scale = last / 255f
        val stepG = size * 3
        val stepB = size * size * 3

        for (i in px.indices) {
            val p = px[i]
            val fr = ((p shr 16) and 0xFF) * scale
            val fg = ((p shr 8) and 0xFF) * scale
            val fb = (p and 0xFF) * scale
            // 输入正好落在最后一个格点上时下界退一格（小数权重为 1，结果仍正确）
            val r0 = fr.toInt().coerceAtMost(last - 1)
            val g0 = fg.toInt().coerceAtMost(last - 1)
            val b0 = fb.toInt().coerceAtMost(last - 1)
            val dr = fr - r0
            val dg = fg - g0
            val db = fb - b0
            val base = ((b0 * size + g0) * size + r0) * 3

            val nr = blend((p shr 16) and 0xFF, trilinear(rgb, base, 0, stepG, stepB, dr, dg, db), t)
            val ng = blend((p shr 8) and 0xFF, trilinear(rgb, base, 1, stepG, stepB, dr, dg, db), t)
            val nb = blend(p and 0xFF, trilinear(rgb, base, 2, stepG, stepB, dr, dg, db), t)
            // alpha 原样保留，三通道一次写回
            px[i] = (p and ALPHA_MASK) or (nr shl 16) or (ng shl 8) or nb
        }

        target.setPixels(px, 0, w, 0, 0, w, h)
        return target
    }

    /** 包围盒 8 个角点的三线性插值；[ch] 是通道偏移（0=r, 1=g, 2=b），返回 0..1。 */
    private fun trilinear(
        rgb: FloatArray,
        base: Int,
        ch: Int,
        stepG: Int,
        stepB: Int,
        dr: Float,
        dg: Float,
        db: Float,
    ): Float {
        val c000 = rgb[base + ch]
        val c100 = rgb[base + 3 + ch]
        val c010 = rgb[base + stepG + ch]
        val c110 = rgb[base + stepG + 3 + ch]
        val c001 = rgb[base + stepB + ch]
        val c101 = rgb[base + stepB + 3 + ch]
        val c011 = rgb[base + stepB + stepG + ch]
        val c111 = rgb[base + stepB + stepG + 3 + ch]

        val r00 = c000 + (c100 - c000) * dr
        val r10 = c010 + (c110 - c010) * dr
        val r01 = c001 + (c101 - c001) * dr
        val r11 = c011 + (c111 - c011) * dr
        val g0v = r00 + (r10 - r00) * dg
        val g1v = r01 + (r11 - r01) * dg
        return g0v + (g1v - g0v) * db
    }

    /** 强度合成：`out = orig + (lut - orig) * t`，结果夹到 0..255。 */
    private fun blend(orig: Int, lutValue: Float, t: Float): Int =
        (orig + (lutValue * 255f - orig) * t).roundToInt().coerceIn(0, 255)
}

/**
 * 内置滤镜清单：资产里 [ASSET_DIR] 目录下与 [ids] 同名的 `<id>.cube`（33³，由 `tools/gen_luts.py` 生成）。
 *
 * 这里只有名字。加载与缓存由渲染管线负责（Task 2 的 `LutLoader`），
 * 「有哪些滤镜」和「怎么把表装进渲染」是两件事。
 */
object FilterPresets {

    /** 相对 assets 根目录的子目录。 */
    const val ASSET_DIR = "filters"

    /** 滤镜 id，顺序即界面上的展示顺序。 */
    val ids: List<String> = listOf("creamy", "coolwhite", "retro", "moody", "airy", "mono")
}
