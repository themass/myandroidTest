package com.openapi.ks.myapp.dh

/**
 * 通话页主题色 → Live2D 舞台清屏色（glClearColor）的换算与去重。
 *
 * 背景失效的根因：`libchatwaifu-live2d.so` 的 `LAppDelegate::Run()` 每帧
 * `glClearColor(0,0,0,1)` 写死黑，GLSurfaceView 自己把页面背景盖死——主题再换
 * 也透不出来。修法是 fork .so 开一个 `nativeSetBackgroundColor`，由这里把主题
 * 折算成每帧清屏色推进去（构建：scripts/dh-lab/build-live2d-native.sh）。
 *
 * 三段渐变没有第三维可传（native 只有一个色），折成一色：人物永远站屏幕中部，
 * 中段权重加倍（1-2-1），观感上最接近原来 Compose 渐变在人物身后的那一段。
 */

/** 三段渐变（ARGB）折一枚不透明舞台色；alpha 一律钉 0xFF（透明洞会露出窗口底色）。 */
fun stageClearColorArgb(topArgb: Int, midArgb: Int, bottomArgb: Int): Int {
    fun channel(shift: Int) =
        ((topArgb shr shift and 0xFF) + 2 * (midArgb shr shift and 0xFF) + (bottomArgb shr shift and 0xFF)) / 4
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** ARGB → native 要的 r,g,b ∈ 0..1（alpha 不参与，clearColor 已钉不透明）。 */
fun argbToNormRgb(argb: Int): Triple<Float, Float, Float> = Triple(
    (argb shr 16 and 0xFF) / 255f,
    (argb shr 8 and 0xFF) / 255f,
    (argb and 0xFF) / 255f,
)

/**
 * 主题色要不要推给 native：返回要推的 argb，null = 不推（颜色没变 / 无主题）。
 * 纯函数是为了把「每次 recomposition 都发一次 JNI」这种抖动钉死。
 */
fun stageBackgroundPush(argb: Int?, lastPushedArgb: Int?): Int? =
    argb?.takeIf { it != lastPushedArgb }
