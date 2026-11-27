package com.openapi.feature.camerafx

/**
 * 按部位选贴纸锚点（纯函数，JVM 单测钉死）。
 *
 * 一期只有头顶一路（[stickerAtHeadTop]）。贴纸扩到头顶/眼/嘴/手四类后，
 * 「哪张贴纸挂哪个锚点」必须集中在这一个地方——**跟踪一律复用 [FxAnalyzer]
 * 已经算好的 landmark 结果**（`FaceFx.headTop` / `eyeLine` / `mouth`、`HandFx.points`），
 * 这里不重新解 landmark、不重写跟踪。
 *
 * 宽度口径按部位分开：
 * - 头顶类 = 脸宽（头饰要盖住头）；
 * - 墨镜类 = **两眼距**（按脸宽会盖到耳朵，真机上像面具）；
 * - 胡子类 = 唇宽（由唇 landmark 外接框给出）；
 * - 手持类 = 手掌跨度（腕→中指根的两倍近似）。
 */

/** 一路贴纸的锚点 + 归一化宽 + 倾角（倾角沿用两眼连线，与头顶贴纸同一口径）。 */
data class StickerAnchorSource(
    /** 贴纸中心（归一化正立图像）。 */
    val anchor: Point2d,
    /** 贴纸宽（归一化图像全宽的比例）——按部位换算，见文件注释。 */
    val widthNorm: Float,
    /** 显示倾角（已按前摄镜像反号，与 HUD/头顶贴纸同源）。 */
    val tiltDeg: Float,
)

/** 外唇 landmark 的均值 = 唇锚点。空输入回 null（没检测到是常态，不抛）。 */
fun mouthAnchor(lipPoints: List<Point2d>): Point2d? {
    if (lipPoints.isEmpty()) return null
    var sx = 0f
    var sy = 0f
    for (p in lipPoints) {
        sx += p.x
        sy += p.y
    }
    return Point2d(sx / lipPoints.size, sy / lipPoints.size)
}

/** 唇宽（归一化）：外唇 landmark 的 x 跨度。少于两点回 0。 */
fun mouthWidthNorm(lipPoints: List<Point2d>): Float {
    if (lipPoints.size < 2) return 0f
    return (lipPoints.maxOf { it.x } - lipPoints.minOf { it.x }).coerceAtLeast(0f)
}

/** 两眼距（归一化）：墨镜宽度的基准。 */
fun eyeDistanceNorm(face: FaceFx): Float {
    val line = face.eyeLine ?: return 0f
    return pointDist(line.a, line.b)
}

/**
 * 手掌中心（21 点均值）+ 手掌跨度。点不足 21 一律回 null——
 * 与 [CameraSwipeTracker] 同纪律：数据不全就当这只手不可用，不猜。
 */
fun handAnchor(points: List<Point2d>): Pair<Point2d, Float>? {
    if (points.size < HAND_LANDMARK_COUNT) return null
    var sx = 0f
    var sy = 0f
    for (p in points) {
        sx += p.x
        sy += p.y
    }
    val centre = Point2d(sx / points.size, sy / points.size)
    // 跨度：腕(0) 到中指根(9) 的距离的两倍 ≈ 整只手占的宽度。
    val span = pointDist(points[0], points[9]) * 2f
    return centre to span.coerceAtLeast(0.02f)
}

/**
 * 单张贴纸（已知 face / hand）的锚点。缺所需信号回 null → 这一帧不画。
 *
 * [tiltForDisplay] 由调用方给（与 [CameraFxGrantedContent] 里 HUD 同一 mirrorX 口径）。
 */
fun stickerAnchorFor(
    stickerAssetId: String,
    face: FaceFx?,
    hand: HandFx?,
    tiltForDisplay: Float = 0f,
    lips: List<Point2d> = emptyList(),
): Point2d? {
    val spec = StickerAssetIds.lookup(stickerAssetId) ?: return null
    return when (spec.part) {
        StickerAnchorPart.HEAD_TOP -> face?.headTop
        StickerAnchorPart.EYES -> face?.eyeLine?.let {
            Point2d((it.a.x + it.b.x) / 2f, (it.a.y + it.b.y) / 2f)
        }
        // 唇锚点优先用 FrameFx 里已挂好的 face.mouth（FxAnalyzer 每帧算一次），
        // 退化路径才现算 landmark 均值——保证面板与预览走同一个数。
        StickerAnchorPart.MOUTH -> face?.mouth ?: mouthAnchor(lips)
        StickerAnchorPart.HAND -> handAnchor(hand?.points.orEmpty())?.first
    }
}

/**
 * 一帧里所有该画贴纸的锚点（按 face/hand 下标一路一路给）。
 *
 * 下标不是身份（MediaPipe 不保证多脸顺序稳定）——沿用 [PlacementSmoother] 的
 * snap 阈值处理换人，与头顶贴纸完全同一套办法。
 */
fun stickerAnchorsForFrame(
    stickerAssetId: String,
    frame: FrameFx,
): List<StickerAnchorSource> {
    val spec = StickerAssetIds.lookup(stickerAssetId) ?: return emptyList()
    return when (spec.part) {
        StickerAnchorPart.HEAD_TOP -> frame.faces.mapNotNull { f ->
            val top = f.headTop ?: return@mapNotNull null
            val fw = f.bounds?.width ?: return@mapNotNull null
            StickerAnchorSource(top, fw * 1.05f, f.tiltDeg)
        }
        StickerAnchorPart.EYES -> frame.faces.mapNotNull { f ->
            val line = f.eyeLine ?: return@mapNotNull null
            val dist = eyeDistanceNorm(f)
            if (dist <= 0f) return@mapNotNull null
            StickerAnchorSource(
                anchor = Point2d((line.a.x + line.b.x) / 2f, (line.a.y + line.b.y) / 2f),
                // 眼距 0.2 → 墨镜 ~0.28 宽：略宽于两眼，盖到眉骨下缘。
                widthNorm = dist * 1.4f,
                tiltDeg = lineTiltDegrees(line.a, line.b, mirrorX = true),
            )
        }
        StickerAnchorPart.MOUTH -> frame.faces.mapNotNull { f ->
            val mouth = f.mouth ?: return@mapNotNull null
            val fw = f.bounds?.width ?: return@mapNotNull null
            // 胡子按**脸宽**给尺寸（唇宽在归一化坐标里太小、抖动也大）：0.55 倍脸宽 ≈ 唇区宽。
            StickerAnchorSource(mouth, fw * 0.55f, f.tiltDeg)
        }
        StickerAnchorPart.HAND -> frame.hands.mapNotNull { h ->
            val (centre, span) = handAnchor(h.points) ?: return@mapNotNull null
            StickerAnchorSource(centre, span, 0f)
        }
    }
}

/**
 * 锚点 → 画布摆放（头顶版 [stickerAtHeadTop] 的按部位泛化）。
 *
 * 与 [CanvasMap] 同一 FILL_CENTER + mirrorX 语义，尺寸同样 clamp 到 24..512px
 * （过小看不见、过大盖满屏）。
 */
fun stickerPlacementFor(
    map: CanvasMap,
    source: StickerAnchorSource,
): StickerPlacementPx {
    val m = map.map(source.anchor)
    val w = (source.widthNorm * map.imgW * map.scalePx).coerceIn(24f, 512f)
    return StickerPlacementPx(
        centerX = m.x,
        centerY = m.y,
        sizePx = w,
        rotationDeg = source.tiltDeg,
    )
}
