package com.openapi.imaging.cropassist

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.openapi.imaging.CropNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Real neural-network document-corner detector, fully on-device with zero runtime download —
 * the thing OpenCV's heuristics and the company's (undownloadable) MNN model can't both offer
 * at once.
 *
 * Model: **DocQuadNet256** (MobileNetV3 backbone + lightweight FPN, corner-heatmap regression),
 * from the open-source [egdels/makeacopy](https://github.com/egdels/makeacopy) project. Its
 * README states: "The exported ONNX inference model is an independently created work and is
 * licensed under the Apache License 2.0" — bundled here as
 * `assets/docquad/docquadnet256_trained_opset17.ort` (13.4 MB), run via the standard (non-minimal)
 * `com.microsoft.onnxruntime:onnxruntime-android` runtime; makeacopy's own operator-stripped
 * custom ONNX Runtime build is an optional size optimization we don't need.
 *
 * Model I/O (per makeacopy's `training/README.md`):
 *  - input: `1x3x256x256` float32 RGB, 0..1, NCHW, letterboxed onto a black square canvas
 *  - output `corner_heatmaps`: `1x4x64x64`, one heatmap per document corner
 *  - output `mask_logits`: `1x1x64x64` (unused here — only the 4 corner peaks are needed)
 */
class DocQuadNetCropAssistProvider(context: Context) : CropAssistProvider {
    override val id: String = "docquadnet"
    override val displayName: String = "DocQuadNet(端上神经网络)"

    private val appContext = context.applicationContext

    private val session: OrtSession? by lazy {
        runCatching {
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = appContext.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
            OrtSession.SessionOptions().use { options -> env.createSession(modelBytes, options) }
        }.onFailure { Log.e(TAG, "failed to load DocQuadNet model", it) }.getOrNull()
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) { session != null }

    override suspend fun suggest(bitmap: Bitmap): CropAssistResult = withContext(Dispatchers.Default) {
        val ortSession = session ?: return@withContext CropAssistResult.Unavailable("模型加载失败")
        val start = System.currentTimeMillis()
        runCatching { detect(ortSession, bitmap) }
            .onFailure { Log.e(TAG, "inference failed", it) }
            .getOrNull()
            ?.let { norm ->
                CropAssistResult.SuggestedBox(
                    norm = norm,
                    confidenceLabel = "DocQuadNet",
                    elapsedMs = System.currentTimeMillis() - start,
                )
            }
            ?: CropAssistResult.Unavailable("推理失败")
    }

    private fun detect(session: OrtSession, bitmap: Bitmap): CropNorm? {
        val env = OrtEnvironment.getEnvironment()
        val srcW = bitmap.width
        val srcH = bitmap.height

        // Letterbox onto a black INPUT_SIZE x INPUT_SIZE canvas, matching training preprocessing.
        val scale = min(INPUT_SIZE.toFloat() / srcW, INPUT_SIZE.toFloat() / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val offsetX = (INPUT_SIZE - scaledW) / 2
        val offsetY = (INPUT_SIZE - scaledH) / 2

        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(letterboxed).apply {
            drawColor(Color.BLACK)
            drawBitmap(Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true), offsetX.toFloat(), offsetY.toFloat(), null)
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        letterboxed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val plane = INPUT_SIZE * INPUT_SIZE
        val chw = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f
            chw[plane + i] = ((p shr 8) and 0xFF) / 255f
            chw[2 * plane + i] = (p and 0xFF) / 255f
        }

        val inputName = session.inputNames.firstOrNull() ?: return null
        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )

        val heatmapBuffer = inputTensor.use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val cornerValue = result.get(CORNER_HEATMAPS_OUTPUT).orElse(null) as? OnnxTensor
                    ?: return null
                cornerValue.floatBuffer?.let { FloatArray(it.remaining()).also(it::get) }
            }
        } ?: return null

        // 4 channels of 64x64 heatmaps; the peak of each channel is that corner's location.
        // Order among the 4 corners doesn't matter here since we only need their bounding box.
        val points = (0 until 4).map { channel ->
            var bestIdx = 0
            var bestVal = Float.NEGATIVE_INFINITY
            val base = channel * HEATMAP_SIZE * HEATMAP_SIZE
            for (i in 0 until HEATMAP_SIZE * HEATMAP_SIZE) {
                val v = heatmapBuffer[base + i]
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = i
                }
            }
            val hy = bestIdx / HEATMAP_SIZE
            val hx = bestIdx % HEATMAP_SIZE
            // Heatmap cell -> canvas pixel (heatmap covers the full INPUT_SIZE canvas).
            val canvasX = (hx + 0.5f) / HEATMAP_SIZE * INPUT_SIZE
            val canvasY = (hy + 0.5f) / HEATMAP_SIZE * INPUT_SIZE
            // Undo the letterbox to get back to original-bitmap pixel coordinates.
            val origX = (canvasX - offsetX) / scale
            val origY = (canvasY - offsetY) / scale
            origX.coerceIn(0f, srcW.toFloat()) to origY.coerceIn(0f, srcH.toFloat())
        }

        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        if (maxX <= minX || maxY <= minY) return null

        return CropNorm(
            left = minX / srcW,
            top = minY / srcH,
            right = maxX / srcW,
            bottom = maxY / srcH,
        )
    }

    private companion object {
        const val TAG = "DocQuadNetCropAssist"
        const val MODEL_ASSET_PATH = "docquad/docquadnet256_trained_opset17.ort"
        const val CORNER_HEATMAPS_OUTPUT = "corner_heatmaps"
        const val INPUT_SIZE = 256
        const val HEATMAP_SIZE = 64
    }
}
