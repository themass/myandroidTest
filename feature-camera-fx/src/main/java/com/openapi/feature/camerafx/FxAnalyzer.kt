package com.openapi.feature.camerafx

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 一帧的识别结果（UI 只消费这个）：关键点已按稀疏表采样、锚点已用纯函数算好，
 * Canvas 只负责映射 + 画，不再有任何计算。
 */
data class FaceFx(
    /** 稀疏采样的折线（外廓/眉/眼/唇），每条首尾自动闭合。 */
    val polylines: List<List<Point2d>>,
    val bounds: Rect2d?,
    val headTop: Point2d?,
    val eyeLine: Segment2d?,
    val tiltDeg: Float,
)

data class PoseFx(val segments: List<Segment2d>)

data class FrameFx(
    val faces: List<FaceFx>,
    val poses: List<PoseFx>,
    /** 分析图的正立宽高（toBitmap 已应用旋转），归一化坐标就是相对它说的。 */
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
            // RGBA 输出（见 ImageAnalysis.Builder），toBitmap 直接可用且已应用旋转，
            // 得到正立位图后归一化坐标语义 = 正立画面，不再需要 ImageProcessingOptions。
            val bmp = image.toBitmap()
            val mp = BitmapImageBuilder(bmp).build()
            val tsNanos = System.nanoTime()
            val tsMs = tsNanos / 1_000_000L
            val faceResult = face!!.detectForVideo(mp, tsMs)
            val poseResult = pose!!.detectForVideo(mp, tsMs)
            val fx = buildFrame(faceResult.faceLandmarks(), poseResult.landmarks(), mp.width, mp.height)
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
                    oval,
                    pick(lm, FACE_BROW_LEFT),
                    pick(lm, FACE_BROW_RIGHT),
                    pick(lm, FACE_EYE_LEFT),
                    pick(lm, FACE_EYE_RIGHT),
                    pick(lm, FACE_LIPS_OUTER),
                ).filter { it.size >= 2 },
                bounds = bounds,
                headTop = headTopAnchor(oval, bounds),
                eyeLine = eyes,
                tiltDeg = eyes?.let { eyeTiltDegrees(it.a, it.b) } ?: 0f,
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
