package com.aimath.paizhao.photosolve.image

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Real-ESRGAN `realesr-general-x4v3`（SRVGGNetCompact，4 倍）端上超分。
 *
 * 许可：Real-ESRGAN 为 BSD-3-Clause（Copyright (c) 2021, Xintao Wang）。
 * 模型文件不随仓库提交，放到 `assets/superres/realesr-general-x4v3.onnx` 即启用；
 * 缺失时 [isAvailable] 为 false，[upscaleSmall] 返回 null，调用方继续用原图。
 *
 * 模型 I/O：输入 NCHW float32 RGB，取值 0..1；输出 4 倍尺寸，同样 NCHW。
 * 输入尺寸若固定（例如 128x128），边缘不足一块的切片补齐到模型尺寸，输出再裁回。
 * 切块与拼接复用 [TileGeometry] / [SuperResTiler]，接缝由有效区域拼接保证。
 */
class RealEsrganUpscaler(context: Context) {

    private val appContext = context.applicationContext

    private val session: OrtSession? by lazy { loadSession() }

    /** 固定输入尺寸（高、宽）；动态尺寸模型为 null。 */
    private val fixedInput: Pair<Int, Int>? by lazy {
        val s = session ?: return@lazy null
        val info = s.inputInfo.values.firstOrNull()?.info as? TensorInfo ?: return@lazy null
        val shape = info.shape
        if (shape.size == 4 && shape[2] > 0 && shape[3] > 0) {
            shape[2].toInt() to shape[3].toInt()
        } else {
            null
        }
    }

    fun isAvailable(): Boolean = session != null

    /**
     * 对小图做超分。输出像素数随原图面积的 16 倍增长，因此只处理不超过 [MAX_SOURCE_PIXELS] 的图；
     * 更大的图直接返回 null，避免两张 ARGB 大图同时占用内存。
     */
    suspend fun upscaleSmall(source: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        if (session == null) return@withContext null
        if (source.width.toLong() * source.height > MAX_SOURCE_PIXELS) return@withContext null
        runCatching { upscale(source) }
            .onFailure { Log.e(TAG, "super-resolution failed", it) }
            .getOrNull()
    }

    private fun upscale(source: Bitmap): Bitmap {
        val s = checkNotNull(session)
        val fixed = fixedInput
        val tileSize = fixed?.second ?: DEFAULT_TILE
        val tiles = SuperResTiler.plan(source.width, source.height, tileSize, DEFAULT_OVERLAP)
        val results = tiles.map { tile ->
            val crop = SuperResTiler.cropTile(source, tile)
            val inH = fixed?.first ?: crop.height
            val inW = fixed?.second ?: crop.width
            val upscaled = runTile(s, crop, inW, inH)
            crop.recycle()
            // 输出是模型尺寸的 4 倍；只保留左上角与切块对应的区域
            val cut = Bitmap.createBitmap(upscaled, 0, 0, tile.srcWidth * SCALE, tile.srcHeight * SCALE)
            upscaled.recycle()
            tile to cut
        }
        val out = SuperResTiler.stitch(results, source.width, source.height, SCALE)
        results.forEach { it.second.recycle() }
        return out
    }

    private fun runTile(s: OrtSession, crop: Bitmap, inW: Int, inH: Int): Bitmap {
        val padded = Bitmap.createBitmap(inW, inH, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(Color.BLACK)
            drawBitmap(crop, 0f, 0f, null)
        }
        val pixels = IntArray(inW * inH)
        padded.getPixels(pixels, 0, inW, 0, 0, inW, inH)
        padded.recycle()

        val plane = inW * inH
        val chw = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f
            chw[plane + i] = ((p shr 8) and 0xFF) / 255f
            chw[2 * plane + i] = (p and 0xFF) / 255f
        }

        val env = OrtEnvironment.getEnvironment()
        val inputName = s.inputNames.first()
        val input = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, 3, inH.toLong(), inW.toLong()),
        )
        return input.use { tensor ->
            s.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val outW = inW * SCALE
                val outH = inH * SCALE
                val outPlane = outW * outH
                val buf = FloatArray(3 * outPlane)
                output.floatBuffer.get(buf)
                val argb = IntArray(outPlane) { i ->
                    val r = toByte(buf[i])
                    val g = toByte(buf[outPlane + i])
                    val b = toByte(buf[2 * outPlane + i])
                    Color.argb(255, r, g, b)
                }
                Bitmap.createBitmap(argb, outW, outH, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun toByte(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun loadSession(): OrtSession? = runCatching {
        val env = OrtEnvironment.getEnvironment()
        val bytes = appContext.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
        OrtSession.SessionOptions().use { options -> env.createSession(bytes, options) }
    }.onFailure {
        // 模型未随包提供是正常情况，不作为错误记录
        Log.i(TAG, "super-resolution model not bundled ($MODEL_ASSET_PATH), feature disabled")
    }.getOrNull()

    companion object {
        private const val TAG = "RealEsrganUpscaler"
        const val MODEL_ASSET_PATH = "superres/realesr-general-x4v3.onnx"
        const val SCALE = 4
        /** 动态尺寸模型时使用的切块大小（含重叠）。 */
        private const val DEFAULT_TILE = 128
        private const val DEFAULT_OVERLAP = 16
        /** 允许超分的原图像素上限（640x640）。输出约为原图的 16 倍，超过后内存压力过大。 */
        const val MAX_SOURCE_PIXELS = 640L * 640L
    }
}
