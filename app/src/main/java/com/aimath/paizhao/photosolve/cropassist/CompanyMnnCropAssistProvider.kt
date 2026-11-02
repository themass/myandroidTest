package com.aimath.paizhao.photosolve.cropassist

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aimath.paizhao.photosolve.CropNorm
import com.aimath.paizhao.photosolve.cropassist.company.DocOcrDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps the ported company native detector ([DocOcrDetector]). Reports [CropAssistResult.Unavailable]
 * whenever the `.mnn` weight files aren't present under `filesDir/app_ocr_model/...` — the same
 * way aixue's own app degrades when its model hasn't finished downloading, since we don't have
 * access to the internal CDN that fetches them there. See the class doc on [DocOcrDetector].
 */
class CompanyMnnCropAssistProvider(private val appContext: Context) : CropAssistProvider {
    override val id: String = "company_mnn"
    override val displayName: String = "公司算法(MNN)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        DocOcrDetector.create(appContext)?.also { it.close() } != null
    }

    override suspend fun suggest(bitmap: Bitmap): CropAssistResult = withContext(Dispatchers.Default) {
        val detector = DocOcrDetector.create(appContext)
            ?: return@withContext CropAssistResult.Unavailable("模型文件未就绪(app_ocr_model/*.mnn 缺失)")

        try {
            val start = System.currentTimeMillis()
            val detections = runCatching { detector.detect(bitmap) }
                .onFailure { Log.e(TAG, "detect failed", it) }
                .getOrNull()
                .orEmpty()

            val best = detections.maxByOrNull { it.confidence }
                ?: return@withContext CropAssistResult.Unavailable("未检测到题目区域")

            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            CropAssistResult.SuggestedBox(
                norm = CropNorm(
                    left = (best.x1 / w).coerceIn(0f, 1f),
                    top = (best.y1 / h).coerceIn(0f, 1f),
                    right = (best.x2 / w).coerceIn(0f, 1f),
                    bottom = (best.y2 / h).coerceIn(0f, 1f),
                ),
                confidenceLabel = "置信度 ${"%.0f".format(best.confidence * 100)}%",
                elapsedMs = System.currentTimeMillis() - start,
            )
        } finally {
            detector.close()
        }
    }

    private companion object {
        const val TAG = "CompanyMnnCropAssist"
    }
}
