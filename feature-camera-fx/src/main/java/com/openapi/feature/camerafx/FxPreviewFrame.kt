package com.openapi.feature.camerafx

/**
 * 预览直绘路径（真机 P0-A：通话页小窗不用 PreviewView，改画分析帧）的纯决策层。
 *
 * 为什么小窗不用 PreviewView：Live2D 舞台是 GLSurfaceView（默认 z-order 挖孔合成），
 * 麒麟（ADY-AL00）上同一窗口再挂第二个 surface-backed 视图（PreviewView 的
 * COMPATIBLE=TextureView / PERFORMANCE=SurfaceView 两种实现都是）会把两个区域一起
 * 合成黑屏——相机 HAL 明明在出帧。分析帧走 Compose 普通绘制，窗口里就只剩舞台一个
 * 独立 surface。代价=预览帧率=分析帧率（~5-12fps），108x144dp 小窗可接受。
 */

/** 预览副本的长边上限：小窗 108x144dp，720p 全尺寸上传 GPU 是纯浪费。 */
const val PREVIEW_MAX_DIM = 360

/**
 * 预览副本的缩放尺寸：长边压到 [maxDim]，只缩不放（小缓冲没必要放大）。
 * 宽高比保持不变；0/负数输入回退 (0,0)（调用方据此跳过发布）。
 */
fun previewDownscaleSize(width: Int, height: Int, maxDim: Int = PREVIEW_MAX_DIM): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || maxDim <= 0) return 0 to 0
    val long = maxOf(width, height)
    if (long <= maxDim) return width to height
    val scale = maxDim.toFloat() / long
    val w = (width * scale).toInt().coerceAtLeast(1)
    val h = (height * scale).toInt().coerceAtLeast(1)
    return w to h
}
