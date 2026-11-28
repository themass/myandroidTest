package com.openapi.feature.camerafx

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 一帧的识别结果（UI 只消费这个）：关键点已按稀疏表采样、锚点已用纯函数算好，
 * Canvas 只负责映射 + 画，不再有任何计算。
 */
/** 一条稀疏折线；[closed]=false 用于眉（开路径），true 用于脸廓/眼/唇。 */
data class FacePolyline(val points: List<Point2d>, val closed: Boolean)

data class FaceFx(
    /** 稀疏采样的折线（外廓/眉/眼/唇）。 */
    val polylines: List<FacePolyline>,
    val bounds: Rect2d?,
    val headTop: Point2d?,
    val eyeLine: Segment2d?,
    val tiltDeg: Float,
    /**
     * 唇锚点 = 外唇 landmark 均值（[mouthAnchor]），每帧算一次挂在帧上。
     *
     * 挂在这里而不是让贴纸层每帧自己均值：面板判定、预览绘制、HUD 必须用同一个数，
     * 否则「同一张贴纸在两个地方位置不同」这种 bug 查起来极贵。
     */
    val mouth: Point2d? = null,
)

data class PoseFx(val segments: List<Segment2d>)

data class HandFx(
    val segments: List<Segment2d>,
    val gesture: HandGestureKind,
    /**
     * 21 点原始归一化坐标（含指尖），供滑动跟踪用。
     *
     * 为什么不复用 [segments]：连线是有向端点对，指尖要按索引语义取（8=食指尖），
     * 从连线里反解既绕又要重排；而且 `segments` 会因索引越界少线。点数不足 21 时为空
     * （滑动跟踪据此判定「这只手不可用」，不猜）。
     */
    val points: List<Point2d> = emptyList(),
)

data class FrameFx(
    val faces: List<FaceFx>,
    val poses: List<PoseFx>,
    val hands: List<HandFx>,
    /** 正立显示空间的宽高（已按 rotationDegrees 交换）；归一化坐标就是相对它说的。 */
    val imgW: Int,
    val imgH: Int,
    val seq: Long,
    /** 平滑后的识别帧率（每 1 秒更新一次）。 */
    val fps: Float,
)

/**
 * CameraX 分析回调 → MediaPipe detectForVideo → [FrameFx]。
 *
 * 隐私红线：这一层是人脸数据存在的全部范围——不写文件、不上网络、
 * 不 log 关键点数值（错误信息只含异常类名，不含画面内容）。
 *
 * 线程：CameraX analysis executor。结果经 [onFrame] 交给 StateFlow（线程安全）。
 * 背压：KEEP_ONLY_LATEST + inFlight 丢帧——识别慢于 30fps 是预期，绝不排队积压。
 *
 * 关闭：[close] 把模型释放排入 [analysisExecutor]（见 [FxCloseGate]）。
 * **调用方纪律：先 [close] 再 `analysisExecutor.shutdown()`，且 dispose 时先解绑相机**——
 * 顺序走反就是真机 SIGSEGV 的原样复刻。
 */
class FxAnalyzer(
    private val context: Context,
    private val analysisExecutor: Executor,
    private val onFrame: (FrameFx) -> Unit,
    private val onFatal: (String) -> Unit,
    /** 姿态/手部每 N 帧跑一次（脸每帧都跑）：三模型全帧 ~12fps，降半 pose+hand 换帧率。 */
    private val poseEvery: Int = 2,
    /**
     * 预览画面回调（真机 P0-A：通话页小窗换「分析帧直绘」路径，见 CameraFxGrantedContent
     * 的 [framePreview]）。收到的是**正立、未镜像、已裁到 crop** 的帧，长边 ≤ [PREVIEW_MAX_DIM]；
     * 所有权移交调用方（不回收，交给 GC）。在 detect 之前发布——模型慢/挂不影响预览。
     */
    private val onPreviewFrame: ((android.graphics.Bitmap) -> Unit)? = null,
) : ImageAnalysis.Analyzer {

    private val inFlight = AtomicBoolean(false)
    /** 关闭协议（纯 JVM 决策层，FxCloseGateTest 钉死）：closed 标志 + 释放排入分析队列。 */
    private val closeGate = FxCloseGate(this::releaseModels)
    private var face: FaceLandmarker? = null
    private var pose: PoseLandmarker? = null
    private var hand: HandLandmarker? = null
    /** 手部模型加载或推理失败时跳过 hand，避免拖垮脸/姿态（vivo 上曾连续失败）。 */
    private var handUnavailable = false
    private var disabled = false
    private var consecutiveErrors = 0

    private var seq = 0L
    private var fps = 0f
    private var windowStartNanos = 0L
    private var windowFrames = 0

    // 姿态降频（poseEvery）期间沿用上一次的骨架；只在 analysis executor 上读写。
    private var lastPoseLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> =
        emptyList()
    private var lastHandLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> =
        emptyList()

    override fun analyze(image: ImageProxy) {
        // 关闭已开始（模型可能已排队释放）：入口直接丢帧，绝不进 detectForVideo。
        // 崩溃真因就是漏了这一段——见 FxCloseGate 的注释。
        if (closeGate.isClosed() || disabled) {
            image.close()
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            image.close()
            return
        }
        try {
            ensureLoaded()
            // 旋转：优先靠 ImageAnalysis.setOutputImageRotationEnabled(true) 让缓冲本身正立
            // （此时 rotationDegrees=0）。若未启用，toBitmap() 仍是 sensor 横图，必须把
            // rotationDegrees 交给 MediaPipe ImageProcessingOptions，并 uprightDims 交换宽高
            // （真机：漏了 → 覆盖层转 90°、倾角 89.4°）。
            //
            // FOV：toBitmap() 是全缓冲；与 PreviewView 同视野的有效区在 cropRect
            // （UseCaseGroup+ViewPort）。必须裁到 crop 再送模型，上报尺寸也用 crop 正立
            // （真机：全缓冲映射 → 框/折线掉到脖子胸口）。
            val rotation = image.imageInfo.rotationDegrees
            val crop = image.cropRect
            val full = image.toBitmap()
            val bmp = if (crop.left == 0 && crop.top == 0 && crop.width() == full.width && crop.height() == full.height) {
                full
            } else {
                android.graphics.Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height())
                    .also { full.recycle() }
            }
            // 预览直绘路径：在 detect 之前发布（模型慢/挂不断预览），且发布的是**独立副本**
            // ——bmp 随后照常 recycle，UI 持有的副本归 GC（见构造器 onPreviewFrame 注释）。
            onPreviewFrame?.let { cb -> cb(previewCopy(bmp)) }
            val mp = BitmapImageBuilder(bmp).build()
            val ipOptions = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            val tsNanos = System.nanoTime()
            val tsMs = tsNanos / 1_000_000L
            val faceResult = face!!.detectForVideo(mp, ipOptions, tsMs)
            // 姿态比脸贵：每 poseEvery 帧跑一次，中间帧沿用上一次的骨架
            val runPose = seq % poseEvery == 0L
            val poseLandmarks = if (runPose) pose!!.detectForVideo(mp, ipOptions, tsMs).landmarks() else lastPoseLandmarks
            val handLandmarks = when {
                handUnavailable -> lastHandLandmarks
                runPose && hand != null -> {
                    try {
                        hand!!.detectForVideo(mp, ipOptions, tsMs).landmarks()
                    } catch (_: Throwable) {
                        handUnavailable = true
                        hand?.close()
                        hand = null
                        emptyList()
                    }
                }
                else -> lastHandLandmarks
            }
            val (w, h) = uprightCropDims(bmp.width, bmp.height, rotation)
            val fx = buildFrame(faceResult.faceLandmarks(), poseLandmarks, handLandmarks, w, h)
            lastPoseLandmarks = poseLandmarks
            lastHandLandmarks = handLandmarks
            mp.close()
            bmp.recycle()

            trackFps(tsNanos)
            onFrame(fx)
            consecutiveErrors = 0
        } catch (t: Throwable) {
            consecutiveErrors += 1
            if (consecutiveErrors >= FATAL_ERROR_LIMIT) {
                disabled = true
                // 只报异常类名 + message：模型/图异常信息不含画面内容，可安全显示
                onFatal("识别失败已停止：${t.javaClass.simpleName}")
            }
        } finally {
            image.close()
            inFlight.set(false)
        }
    }

    /**
     * 预览副本：水平镜像（前摄「用户所见」，与 overlay 的 mirrorX=true 同一口径）
     * + 长边压缩（[previewDownscaleSize]）。独立于 [bmp]（后者随后 recycle），归 GC。
     */
    private fun previewCopy(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val (w, h) = previewDownscaleSize(src.width, src.height)
        if (w <= 0 || h <= 0) return src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val m = android.graphics.Matrix()
        m.preScale(-w.toFloat() / src.width, h.toFloat() / src.height)
        return android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun ensureLoaded() {
        if (face == null) {
            val base = BaseOptions.builder().setModelAssetPath(MODEL_FACE).build()
            face = FaceLandmarker.createFromOptions(
                context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumFaces(1)
                    .build(),
            )
        }
        if (pose == null) {
            val base = BaseOptions.builder().setModelAssetPath(MODEL_POSE).build()
            pose = PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(1)
                    .build(),
            )
        }
        if (hand == null && !handUnavailable) {
            try {
                val base = BaseOptions.builder().setModelAssetPath(MODEL_HAND).build()
                hand = HandLandmarker.createFromOptions(
                    context,
                    HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.VIDEO)
                        .setNumHands(2)
                        .build(),
                )
            } catch (_: Throwable) {
                handUnavailable = true
            }
        }
    }

    private fun buildFrame(
        faceLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>,
        poseLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>,
        handLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>,
        w: Int,
        h: Int,
    ): FrameFx {
        val faces = faceLandmarks.map { lm ->
            val oval = pick(lm, FACE_OVAL)
            val bounds = faceBounds(oval)
            val lips = pick(lm, FACE_LIPS_OUTER)
            val eyes = if (lm.size > max(FACE_IRIS_CENTER_LEFT, FACE_IRIS_CENTER_RIGHT)) {
                Segment2d(lm[FACE_IRIS_CENTER_LEFT].toPoint(), lm[FACE_IRIS_CENTER_RIGHT].toPoint())
            } else {
                null
            }
            FaceFx(
                polylines = listOf(
                    FacePolyline(oval, closed = true),
                    FacePolyline(pick(lm, FACE_BROW_LEFT), closed = false),
                    FacePolyline(pick(lm, FACE_BROW_RIGHT), closed = false),
                    FacePolyline(pick(lm, FACE_EYE_LEFT), closed = true),
                    FacePolyline(pick(lm, FACE_EYE_RIGHT), closed = true),
                    FacePolyline(lips, closed = true),
                ).filter { it.points.size >= 2 },
                bounds = bounds,
                headTop = headTopAnchor(oval, bounds),
                eyeLine = eyes,
                // HUD 显示用户所见（前摄预览镜像空间）的角度
                tiltDeg = eyes?.let { lineTiltDegrees(it.a, it.b, mirrorX = true) } ?: 0f,
                // 胡子贴纸的挂点：复用同一次唇部采样，不再解一遍 landmark。
                mouth = mouthAnchor(lips),
            )
        }
        val poses = poseLandmarks.map { lm ->
            val segments = POSE_CONNECTIONS.mapNotNull { pair ->
                val i = pair[0]
                val j = pair[1]
                if (i !in lm.indices || j !in lm.indices) return@mapNotNull null
                if (lm[i].visibility().orElse(1f) < POSE_VISIBILITY_MIN) return@mapNotNull null
                if (lm[j].visibility().orElse(1f) < POSE_VISIBILITY_MIN) return@mapNotNull null
                Segment2d(lm[i].toPoint(), lm[j].toPoint())
            }
            PoseFx(segments)
        }
        val hands = handLandmarks.map { lm ->
            val pts = lm.map { it.toPoint() }
            val segments = HAND_CONNECTIONS.mapNotNull { pair ->
                val i = pair[0]
                val j = pair[1]
                if (i !in lm.indices || j !in lm.indices) return@mapNotNull null
                Segment2d(lm[i].toPoint(), lm[j].toPoint())
            }
            HandFx(segments = segments, gesture = classifyHandGesture(pts), points = pts)
        }
        seq += 1
        return FrameFx(faces = faces, poses = poses, hands = hands, imgW = w, imgH = h, seq = seq, fps = fps)
    }

    private fun trackFps(nowNanos: Long) {
        windowFrames += 1
        if (windowStartNanos == 0L) {
            windowStartNanos = nowNanos
            return
        }
        val elapsed = nowNanos - windowStartNanos
        if (elapsed >= 1_000_000_000L) {
            fps = windowFrames * 1_000_000_000f / elapsed
            windowStartNanos = nowNanos
            windowFrames = 0
        }
    }

    /**
     * 开始关闭：置 closed（后续帧入口丢弃）并把 [releaseModels] 排入 [analysisExecutor]，
     * 保证释放发生在所有已接受的在途帧之后。幂等；从任意线程调用安全。
     */
    fun close() {
        closeGate.closeOn(analysisExecutor)
    }

    /** 只在分析 executor 上执行（由 [FxCloseGate] 串行保证），与 detect 不交错。 */
    private fun releaseModels() {
        face?.close()
        pose?.close()
        hand?.close()
        face = null
        pose = null
        hand = null
    }

    companion object {
        const val MODEL_FACE = "camerafx/face_landmarker.task"
        const val MODEL_POSE = "camerafx/pose_landmarker_lite.task"
        const val MODEL_HAND = "camerafx/hand_landmarker.task"
        private const val FATAL_ERROR_LIMIT = 5
    }
}
