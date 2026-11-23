package com.openapi.feature.camerafx

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
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
)

data class PoseFx(val segments: List<Segment2d>)

data class FrameFx(
    val faces: List<FaceFx>,
    val poses: List<PoseFx>,
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
 */
class FxAnalyzer(
    private val context: Context,
    private val onFrame: (FrameFx) -> Unit,
    private val onFatal: (String) -> Unit,
    /** 姿态每 N 帧跑一次（脸每帧都跑）：spike 实测全分辨率双模型 ~12fps，降半姿态频率换帧率。 */
    private val poseEvery: Int = 2,
) : ImageAnalysis.Analyzer {

    private val inFlight = AtomicBoolean(false)
    private var face: FaceLandmarker? = null
    private var pose: PoseLandmarker? = null
    private var disabled = false
    private var consecutiveErrors = 0

    private var seq = 0L
    private var fps = 0f
    private var windowStartNanos = 0L
    private var windowFrames = 0

    // 姿态降频（poseEvery）期间沿用上一次的骨架；只在 analysis executor 上读写。
    private var lastPoseLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> =
        emptyList()

    override fun analyze(image: ImageProxy) {
        if (disabled) {
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
            val mp = BitmapImageBuilder(bmp).build()
            val ipOptions = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            val tsNanos = System.nanoTime()
            val tsMs = tsNanos / 1_000_000L
            val faceResult = face!!.detectForVideo(mp, ipOptions, tsMs)
            // 姿态比脸贵：每 poseEvery 帧跑一次，中间帧沿用上一次的骨架
            val runPose = seq % poseEvery == 0L
            val poseLandmarks = if (runPose) pose!!.detectForVideo(mp, ipOptions, tsMs).landmarks() else lastPoseLandmarks
            val (w, h) = uprightCropDims(bmp.width, bmp.height, rotation)
            val fx = buildFrame(faceResult.faceLandmarks(), poseLandmarks, w, h)
            lastPoseLandmarks = poseLandmarks
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
    }

    private fun buildFrame(
        faceLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>,
        poseLandmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>,
        w: Int,
        h: Int,
    ): FrameFx {
        val faces = faceLandmarks.map { lm ->
            val oval = pick(lm, FACE_OVAL)
            val bounds = faceBounds(oval)
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
                    FacePolyline(pick(lm, FACE_LIPS_OUTER), closed = true),
                ).filter { it.points.size >= 2 },
                bounds = bounds,
                headTop = headTopAnchor(oval, bounds),
                eyeLine = eyes,
                // HUD 显示用户所见（前摄预览镜像空间）的角度
                tiltDeg = eyes?.let { lineTiltDegrees(it.a, it.b, mirrorX = true) } ?: 0f,
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
        seq += 1
        return FrameFx(faces = faces, poses = poses, imgW = w, imgH = h, seq = seq, fps = fps)
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

    /** Activity 销毁时调用：释放 native 模型资源。 */
    fun close() {
        face?.close()
        pose?.close()
        face = null
        pose = null
    }

    companion object {
        const val MODEL_FACE = "camerafx/face_landmarker.task"
        const val MODEL_POSE = "camerafx/pose_landmarker_lite.task"
        private const val FATAL_ERROR_LIMIT = 5
    }
}
