package com.openapi.mediaview.edit

/**
 * LUT 测试共用的 .cube 文本夹具：解析用例（[CubeLutParseTest]，纯 JVM）和套表用例
 * （[Lut3DTest]，Robolectric）用的是同一批表，避免两边各写一份还写歪。
 */

/** 2³ 单位表：r 变化最快，所以顺序是 (000)(100)(010)(110)(001)(101)(011)(111)。 */
internal val identityCube2 = """
    TITLE "identity"
    LUT_3D_SIZE 2
    DOMAIN_MIN 0.000000 0.000000 0.000000
    DOMAIN_MAX 1.000000 1.000000 1.000000

    0.000000 0.000000 0.000000
    1.000000 0.000000 0.000000
    0.000000 1.000000 0.000000
    1.000000 1.000000 0.000000
    0.000000 0.000000 1.000000
    1.000000 0.000000 1.000000
    0.000000 1.000000 1.000000
    1.000000 1.000000 1.000000
""".trimIndent() + "\n"

/** 把单通道曲线 [f] 铺成 size³ 的灰阶 .cube 文本（r=g=b，通道间不串扰）。 */
internal fun grayCube(size: Int, f: (Float) -> Float): String {
    val sb = StringBuilder("LUT_3D_SIZE ").append(size).append('\n')
    val last = (size - 1).toFloat()
    for (b in 0 until size) {
        for (g in 0 until size) {
            for (r in 0 until size) {
                val v = f(r / last) // 灰阶：三通道同曲线
                sb.append(v).append(' ').append(f(g / last)).append(' ').append(f(b / last)).append('\n')
            }
        }
    }
    return sb.toString()
}

/**
 * 「抬黑」曲线：纯黑抬到 0.2，0.2 以上原样不动（在 x=0.2 处连续且斜率相接）。
 *
 * 断点选在 16³ 网格点 3/15 上，所以 0.2 以上的格点全是恒等值，插值出来的亮部**必须**
 * 一模一样。全局线性（out = a*x + b）做不到「黑场 +0.2 且亮部不动」——要 b=0.2 就得
 * a=0.8，中灰会被压暗，测试就能抓住。
 */
internal fun liftBlack(x: Float): Float =
    if (x < 0.2f) x + 0.2f * (1f - x / 0.2f) * (1f - x / 0.2f) else x

/** 接近真实资产的调子：对比 1.15 + 黑场轻微抬升（连续性测试用）。 */
internal fun toneCurve(x: Float): Float =
    (0.5f + (x - 0.5f) * 1.15f).coerceIn(0f, 1f) + 0.03f * (1f - x)
