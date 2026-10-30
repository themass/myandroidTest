package com.aimath.paizhao.photosolve.cropassist

import android.graphics.Bitmap
import android.util.Log
import com.aimath.paizhao.photosolve.CropNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Classic OpenCV document-edge detection: grayscale -> blur -> Canny -> findContours -> pick the
 * largest 4-point-ish contour -> bounding box. Same family of technique as well-known OSS
 * document scanners (e.g. LiveEdgeDetection); works best against a reasonably contrasted
 * background and isn't as robust as a trained model, which is exactly why it's offered as one
 * swappable option among several rather than the only one.
 */
class OpenCvCropAssistProvider : CropAssistProvider {
    override val id: String = "opencv"
    override val displayName: String = "OpenCV 边缘检测"

    private val loaded: Boolean by lazy {
        runCatching { OpenCVLoader.initLocal() }.getOrElse {
            Log.e(TAG, "OpenCV init failed", it)
            false
        }
    }

    override suspend fun isAvailable(): Boolean = loaded

    override suspend fun suggest(bitmap: Bitmap): CropAssistResult = withContext(Dispatchers.Default) {
        if (!loaded) return@withContext CropAssistResult.Unavailable("OpenCV 初始化失败")

        val start = System.currentTimeMillis()
        runCatching { detectLargestQuad(bitmap) }
            .onFailure { Log.e(TAG, "detect failed", it) }
            .getOrNull()
            ?.let { norm ->
                CropAssistResult.SuggestedBox(
                    norm = norm,
                    confidenceLabel = "轮廓检测",
                    elapsedMs = System.currentTimeMillis() - start,
                )
            }
            ?: CropAssistResult.Unavailable("未检测到明显边缘")
    }

    private fun detectLargestQuad(bitmap: Bitmap): CropNorm? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.dilate(edges, edges, Mat())

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val imageArea = (bitmap.width * bitmap.height).toDouble()
        var best: MatOfPoint2f? = null
        var bestArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < imageArea * 0.1 || area <= bestArea) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

            if (approx.toArray().size in 4..6) {
                best = approx
                bestArea = area
            }
        }

        val points = best?.toArray() ?: return null
        if (points.isEmpty()) return null

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = 0.0
        var maxY = 0.0
        for (p in points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return CropNorm(
            left = (minX.toFloat() / w).coerceIn(0f, 1f),
            top = (minY.toFloat() / h).coerceIn(0f, 1f),
            right = (maxX.toFloat() / w).coerceIn(0f, 1f),
            bottom = (maxY.toFloat() / h).coerceIn(0f, 1f),
        )
    }

    private companion object {
        const val TAG = "OpenCvCropAssist"
    }
}
