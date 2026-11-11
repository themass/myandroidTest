package com.aimath.paizhao.photosolve.data

import com.aimath.paizhao.photosolve.presentation.PhotoSolveResult

import android.graphics.Bitmap
import com.openapi.common.image.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 拍照解题的真实实现，对接 myconf/python/ocr 的 photosolve 接口：
 * 1. 上传前用 [ImageCompressor] 压缩（长边 1600、JPEG 82、800 KB 以内）；
 * 2. POST /photosolve/upload 拿 imageId；
 * 3. POST /photosolve/solve 解题。
 *
 * 服务端返回 {code, message, data}；code 非 0 时抛出 [PhotoSolveException]，界面按错误码提示
 * （40001 不清晰、40002 不是题目、40003 图片问题、42901 额度、50001 服务失败）。
 *
 * [baseUrl] 形如 `https://host:port/`（末尾斜杠可有可无）。
 */
class HttpPhotoSolveRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : PhotoSolveRepository {

    private val base = baseUrl.trimEnd('/') + "/"

    override suspend fun solve(image: Bitmap): PhotoSolveResult = withContext(Dispatchers.IO) {
        val compressed = ImageCompressor.compress(image)
        val imageId = upload(compressed.bytes)
        val data = execute(
            Request.Builder()
                .url(base + "photosolve/solve")
                .post(JSONObject().put("imageId", imageId).toString().toRequestBody(JSON))
                .build(),
        )
        toResult(question = data.optString("question"), answer = data.optString("answer"))
    }

    private fun upload(bytes: ByteArray): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "question.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val data = execute(Request.Builder().url(base + "photosolve/upload").post(body).build())
        return data.optString("imageId").ifBlank { throw PhotoSolveException(50001, "服务返回异常，请重试") }
    }

    /** 发请求并解析统一结构；网络和解析失败统一按 50001（可重试）处理。 */
    private fun execute(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                val code = json.optInt("code", -1)
                if (code != 0) throw PhotoSolveException(code, json.optString("message", "请求失败"))
                return json.optJSONObject("data") ?: JSONObject()
            }
        } catch (e: PhotoSolveException) {
            throw e
        } catch (e: IOException) {
            throw PhotoSolveException(50001, "网络异常，请重试")
        } catch (e: JSONException) {
            throw PhotoSolveException(50001, "服务返回异常，请重试")
        }
    }

    /** 服务端返回的是一段完整解答（Markdown）：最后一行作为最终答案，前面的行作为步骤。 */
    private fun toResult(question: String, answer: String): PhotoSolveResult {
        val lines = answer.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw PhotoSolveException(50001, "没有收到解答，请重试")
        return PhotoSolveResult(
            recognizedQuestion = question,
            steps = if (lines.size > 1) lines.dropLast(1) else lines,
            finalAnswer = lines.last(),
        )
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
