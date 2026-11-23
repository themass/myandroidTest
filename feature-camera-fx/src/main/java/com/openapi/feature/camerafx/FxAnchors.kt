package com.openapi.feature.camerafx

import kotlin.math.abs
import kotlin.math.atan2

/**
 * 锚点纯函数（spike 的核心交付物）。
 *
 * 全部只在归一化坐标（0..1，y 向下，图像「正立」后）里运算，不碰 Android/Compose 类型，
 * 用 JVM 单测钉死。后续 sticker 跟随要的三个锚点就是这里定义的三个量：
 *   - [faceBounds]      人脸框（归一化外接矩形）
 *   - [headTopAnchor]   头顶锚点（贴头饰类 sticker 的挂点）
 *   - [eyeTiltDegrees]  两眼连线倾角（sticker 随头倾斜旋转）
 */

/** 归一化平面点。z 被刻意丢掉：sticker 是 2D 跟随，先不需要深度。 */
data class Point2d(val x: Float, val y: Float)

/** 归一化矩形（x..x+width, y..y+height）。 */
data class Rect2d(val x: Float, val y: Float, val width: Float, val height: Float)

/** 两点线段（归一化）。 */
data class Segment2d(val a: Point2d, val b: Point2d)

/**
 * 外接矩形。调用方保证 points 非空（空输入返回 null 而不是抛异常，
 * 检测侧「没检测到」是常态，不值得异常控制流）。
 */
fun faceBounds(points: List<Point2d>): Rect2d? {
    if (points.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    return Rect2d(minX, minY, maxX - minX, maxY - minY)
}

/**
 * 头顶锚点：轮廓最高点向上再抬 [earHeightRatio] 倍的人脸框高度。
 * 向上抬是为了给头饰留出「坐在头顶上」的空间，而不是压在额头上。
 */
fun headTopAnchor(outline: List<Point2d>, bounds: Rect2d?, earHeightRatio: Float = 0.12f): Point2d? {
    if (outline.isEmpty() || bounds == null) return null
    val topY = outline.minOf { it.y }
    return Point2d(bounds.x + bounds.width / 2f, topY - bounds.height * earHeightRatio)
}

/**
 * 两眼（虹膜中心）连线倾角，度。正值 = 画面里从 first 到 second 向下倾斜（y 向下）。
 * 两点重合时返回 0，不返回 NaN——下游 rotation 拿到 NaN 会把整层画崩。
 */
fun eyeTiltDegrees(first: Point2d, second: Point2d): Float {
    val dx = second.x - first.x
    val dy = second.y - first.y
    if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) return 0f
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
}

/**
 * 两点（如双眼）连线在「显示空间」的倾角，度，归一到 (-90, 90]（线的角度，不是向量方向）。
 *
 * [mirrorX] 对应前摄预览的显示镜像：关键点来自未镜像的分析图，预览里看到的是镜像后的画面，
 * 镜像会把倾角反号（未镜像 +30° 的头歪，屏幕上看是 -30°）。HUD 要显示用户所见就传 true。
 * 真机缺陷回归（HUD 89.4°）：根因不是弧度/角度，而是关键点落在横屏 sensor 空间没换算
 * （见 [uprightDims]）；换算后倾角就是普通的小角度。
 */
fun lineTiltDegrees(first: Point2d, second: Point2d, mirrorX: Boolean): Float {
    var deg = eyeTiltDegrees(first, second)
    if (mirrorX) deg = -deg
    // 线没有方向：把 atan2 的 (-180,180] 折回 (-90,90]
    while (deg > 90f) deg -= 180f
    while (deg <= -90f) deg += 180f
    return deg
}

/**
 * 帧的「正立显示尺寸」：`ImageProxy.toBitmap()` **不会**应用 rotationDegrees，
 * 关键点默认落在 sensor 空间（竖持手机时是横屏）。必须把 rotationDegrees 交给
 * MediaPipe 的 ImageProcessingOptions，并把宽高按旋转交换后再上报给画布映射。
 * 真机缺陷回归（倾角 89.4°、覆盖层整体转了 90°）就是漏了这一步。
 */
fun uprightDims(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (rotationDegrees == 90 || rotationDegrees == 270) Pair(height, width) else Pair(width, height)

/**
 * 分析帧上报给画布的尺寸：必须用 [ImageProxy.cropRect] 的宽高，不能用全缓冲。
 *
 * `ImageProxy.toBitmap()` 返回的是 sensor 全缓冲；与 PreviewView 同 FOV 的有效区域在
 * cropRect（UseCaseGroup+ViewPort 绑定时尤其如此）。拿全缓冲尺寸去做 CanvasMap，
 * 覆盖层会相对预览整体偏移（真机：框/折线掉到脖子胸口）。
 */
fun uprightCropDims(cropWidth: Int, cropHeight: Int, rotationDegrees: Int): Pair<Int, Int> =
    uprightDims(cropWidth, cropHeight, rotationDegrees)

/**
 * 归一化正立图像 → 画布像素的映射。
 *
 * CameraX 默认预览缩放是 FILL_CENTER：等比放大到铺满画布、超出部分裁掉。
 * [mirrorX] **只处理前置摄像头的显示镜像**（CameraX 1.4.2 的 PreviewView 对前摄默认镜像，
 * 1.5 才有 MirroringMode API）：关键点来自未镜像的分析图，画之前要在归一化空间翻 x。
 * 注意这与「sensor 旋转」是两回事——旋转由 [uprightDims] + ImageProcessingOptions 在
 * 进模型前就换算掉，到这里坐标系已经是正立的；别再往 mirrorX 上挂旋转语义。
 * 映射本身保持纯函数，真机上对齐后这个开关就不动了。
 */
class CanvasMap(
    val canvasW: Float,
    val canvasH: Float,
    val imgW: Int,
    val imgH: Int,
    val fill: Boolean = true,
    val mirrorX: Boolean = true,
) {
    private val nx: (Float) -> Float = { x -> if (mirrorX) 1f - x else x }

    /** 返回画布像素坐标；落在画布外也可能返回（调用方画时自然被裁）。 */
    fun map(p: Point2d): OffsetPx {
        val x = nx(p.x)
        val y = p.y
        if (canvasW <= 0f || canvasH <= 0f || imgW <= 0 || imgH <= 0) return OffsetPx(0f, 0f)
        val sx = canvasW / imgW
        val sy = canvasH / imgH
        if (fill) {
            val s = maxOf(sx, sy)
            return OffsetPx(
                canvasW / 2f + (x - 0.5f) * imgW * s,
                canvasH / 2f + (y - 0.5f) * imgH * s,
            )
        }
        val s = minOf(sx, sy)
        return OffsetPx(
            canvasW / 2f + (x - 0.5f) * imgW * s,
            canvasH / 2f + (y - 0.5f) * imgH * s,
        )
    }

    /** 尺度：归一化 1 单位（图像全宽）在画布上占多少像素。用于等比换算 sticker 尺寸。 */
    val scalePx: Float
        get() {
            if (canvasW <= 0f || canvasH <= 0f || imgW <= 0 || imgH <= 0) return 0f
            return (if (fill) maxOf(canvasW / imgW, canvasH / imgH)
            else minOf(canvasW / imgW, canvasH / imgH)).toFloatOrZero()
        }

    private fun Float.toFloatOrZero(): Float = if (isNaN()) 0f else this
}

/** 画布像素坐标。 */
data class OffsetPx(val x: Float, val y: Float)
