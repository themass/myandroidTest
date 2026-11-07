package com.aimath.paizhao.photosolve.cropassist.company

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.Closeable

/**
 * Ported from aixue's `feature/cropper/ocr/DocOcrDetector.kt` (same company's own internal
 * YOLO/MNN document-layout detector, reused here in another of the company's own apps). The
 * JNI glue and class shape are unchanged; only the logging call was swapped from aixue's
 * internal `AiLog` to plain [android.util.Log] to drop that dependency.
 *
 * This detector is NOT functional out of the box: it loads `*.mnn` weight files from
 * `context.filesDir/app_ocr_model/{detector,orientation}/`, which aixue fetches at runtime from
 * an internal CDN this project has no access to. [create] returns null (and logs why) until
 * those files are placed there by some other means — e.g. `adb push` for local testing.
 */
data class DocOcrBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

data class YoloOrientConfig(
    val minScore: Float = 0.5f,
    val rotate180MinScore: Float = 0.9f,
    val maxAspectRatio: Float = 10.0f,
    val inputSize: Int = 224,
    val numThreads: Int = 2,
)

data class DocOcrConfig(
    val inputSize: Int = 640,
    val numThreads: Int = 2,
    val confThreshold: Float = 0.25f,
    val nmsThreshold: Float = 0.7f,
    val overlapWeight: Float = 0.8f,
    val orientConfig: YoloOrientConfig = YoloOrientConfig(),
)

data class DocOcrDetection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int,
    val className: String,
) {
    val width: Float get() = x2 - x1
    val height: Float get() = y2 - y1
    val area: Float get() = width * height
}

class DocOcrDetector private constructor(
    private var nativeHandle: Long,
) : Closeable {

    fun detect(bitmap: Bitmap, recBox: DocOcrBox? = null): List<DocOcrDetection> {
        if (nativeHandle == 0L) {
            Log.e(TAG, "Detector has been closed")
            return emptyList()
        }
        val safeBitmap = bitmap.ensureArgb8888()
        val flat = DocOcrNativeBridge.nativeDetectBitmap(
            nativeHandle, safeBitmap,
            recBox?.x1 ?: 0f, recBox?.y1 ?: 0f,
            recBox?.x2 ?: 0f, recBox?.y2 ?: 0f,
        )
        if (flat.isEmpty()) return emptyList()
        return buildList(flat.size / 6) {
            var i = 0
            while (i + 5 < flat.size) {
                val classId = flat[i + 5].toInt()
                add(
                    DocOcrDetection(
                        x1 = flat[i],
                        y1 = flat[i + 1],
                        x2 = flat[i + 2],
                        y2 = flat[i + 3],
                        confidence = flat[i + 4],
                        classId = classId,
                        className = DocOcrNativeBridge.nativeClassNameForDetector(nativeHandle, classId),
                    ),
                )
                i += 6
            }
        }
    }

    fun orientPredict(bitmap: Bitmap): Int {
        if (nativeHandle == 0L) {
            Log.e(TAG, "Detector has been closed")
            return 0
        }
        return DocOcrNativeBridge.nativeOrientPredict(nativeHandle, bitmap.ensureArgb8888())
    }

    override fun close() {
        if (nativeHandle != 0L) {
            DocOcrNativeBridge.nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    companion object {
        private const val TAG = "DocOcrDetector"

        fun create(context: Context, config: DocOcrConfig = DocOcrConfig()): DocOcrDetector? {
            val detectorModelFile =
                context.filesDir.resolve("app_ocr_model/detector")
                    .listFiles { f -> f.extension == "mnn" }
                    ?.firstOrNull()
            val orientModelFile =
                context.filesDir.resolve("app_ocr_model/orientation")
                    .listFiles { f -> f.extension == "mnn" }
                    ?.firstOrNull()
            if (detectorModelFile == null || orientModelFile == null) {
                Log.e(
                    TAG,
                    "model file missing: " +
                        "detector=${context.filesDir.resolve("app_ocr_model/detector").absolutePath}/*.mnn exists=${detectorModelFile != null}, " +
                        "orient=${context.filesDir.resolve("app_ocr_model/orientation").absolutePath}/*.mnn exists=${orientModelFile != null}",
                )
                return null
            }
            val handle = DocOcrNativeBridge.nativeCreate(
                modelPath = detectorModelFile.absolutePath,
                orientModelPath = orientModelFile.absolutePath,
                inputSize = config.inputSize,
                numThreads = config.numThreads,
                confThreshold = config.confThreshold,
                nmsThreshold = config.nmsThreshold,
                overlapWeight = config.overlapWeight,
                orientMinScore = config.orientConfig.minScore,
                orientRotate180MinScore = config.orientConfig.rotate180MinScore,
                orientMaxAspectRatio = config.orientConfig.maxAspectRatio,
                orientInputSize = config.orientConfig.inputSize,
                orientNumThreads = config.orientConfig.numThreads,
            )
            if (handle == 0L) {
                Log.e(TAG, "nativeCreate returned 0, model load failed")
                return null
            }
            return DocOcrDetector(handle)
        }
    }
}

private fun Bitmap.ensureArgb8888(): Bitmap =
    if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

internal object DocOcrNativeBridge {
    /**
     * 预编译的 MNN 检测库只提供 arm64-v8a（见 cpp/CMakeLists.txt）。
     * 其它 ABI 上 so 不存在，init 里必须吞掉链接错误，让上层按“不可用”降级，
     * 而不是让整个类初始化失败把应用带崩。
     */
    val isAvailable: Boolean = try {
        System.loadLibrary("c++_shared")
        System.loadLibrary("MNN")
        System.loadLibrary("dococr_jni")
        System.loadLibrary("dococr")
        true
    } catch (t: Throwable) {
        Log.w("DocOcrDetector", "native dococr unavailable on this ABI: ${t.message}")
        false
    }

    external fun nativeCreate(
        modelPath: String,
        orientModelPath: String?,
        inputSize: Int,
        numThreads: Int,
        confThreshold: Float,
        nmsThreshold: Float,
        overlapWeight: Float,
        orientMinScore: Float,
        orientRotate180MinScore: Float,
        orientMaxAspectRatio: Float,
        orientInputSize: Int,
        orientNumThreads: Int,
    ): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeDetectBitmap(
        handle: Long,
        bitmap: Bitmap,
        recX1: Float,
        recY1: Float,
        recX2: Float,
        recY2: Float,
    ): FloatArray

    external fun nativeOrientPredict(handle: Long, bitmap: Bitmap): Int

    external fun nativeClassNameForDetector(handle: Long, classId: Int): String
}
