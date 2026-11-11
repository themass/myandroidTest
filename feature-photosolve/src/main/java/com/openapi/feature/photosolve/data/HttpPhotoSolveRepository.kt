package com.openapi.feature.photosolve.data

import android.graphics.Bitmap

import com.openapi.common.image.ImageCompressor
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 拍照解题的真实实现，对接 testserverV 的 `/api/photosolve/v1/solve.json`
 * （见 testserverV 的 `vvv-link/.../photosolve/PhotoSolveController`）。
 *
 * 一次 multipart 同时完成上传和解题：服务端不保留临时图片，也少一次往返。
 *
 * 请求：multipart/form-data
 * - `images`：一张 JPEG（已压缩）
 * - `clientRequestId`：每次调用一个 UUID，服务端用于日志和去重
 * - `text` / `subject` / `grade`：可选
 *
 * 响应外层是项目统一的 `{errno, data, error}`（不是 `{code, message, data}`）：
 * - `errno == 0` 时读 `data`；
 * - 非 0 时抛 [PhotoSolveException]，`code` 即 errno。
 *
 * `data`（PhotoSolveResponse）：
 * ```
 * { requestId, providerUsed, subject, questionFound,
 *   questions: [ { index, text, latex, finalAnswer, finalAnswerLatex,
 *                  steps: [ { index, text, latex } ], confidence } ] }
 * ```
 * 一张图可能有多个小题，客户端当前只展示第一题；其余小题保留在 [PhotoSolveResult.moreQuestions]。
 *
 * [baseUrl] 形如 `https://host/`，末尾斜杠可有可无。
 */
class HttpPhotoSolveRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : PhotoSolveRepository {

    private val endpoint = baseUrl.trimEnd('/') + "/api/photosolve/v1/solve.json"

    override suspend fun solve(image: Bitmap): PhotoSolveResult = withContext(Dispatchers.IO) {
        val compressed = ImageCompressor.compress(image)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("images", "question.jpg", compressed.bytes.toRequestBody(JPEG))
            .addFormDataPart("clientRequestId", UUID.randomUUID().toString())
            .build()

        val data = execute(Request.Builder().url(endpoint).post(body).build())
        parse(data)
    }

    /** 发请求并取出 data；errno 非 0 时抛出对应错误码。 */
    private fun execute(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = JSONObject(text)
                val errno = json.optInt("errno", -1)
                if (errno != 0) {
                    throw PhotoSolveException(errno, json.optString("error", defaultMessage(errno)))
                }
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

    /** 取第一题作为结果；多题时其余放进 [PhotoSolveResult.moreQuestions]。 */
    private fun parse(data: JSONObject): PhotoSolveResult {
        val questions = data.optJSONArray("questions")
            ?: throw PhotoSolveException(40004, "没有识别到题目内容")
        if (questions.length() == 0) throw PhotoSolveException(40004, "没有识别到题目内容")

        val parsed = (0 until questions.length()).mapNotNull { i -> questions.optJSONObject(i)?.let(::toQuestion) }
        val first = parsed.firstOrNull() ?: throw PhotoSolveException(40004, "没有识别到题目内容")
        return first.copy(moreQuestions = parsed.drop(1))
    }

    private fun toQuestion(q: JSONObject): PhotoSolveResult {
        val steps = ArrayList<String>()
        val stepArray = q.optJSONArray("steps")
        if (stepArray != null) {
            for (i in 0 until stepArray.length()) {
                val step = stepArray.optJSONObject(i) ?: continue
                val text = step.optString("text").trim()
                val latex = step.optString("latex").trim()
                if (text.isNotEmpty() || latex.isNotEmpty()) {
                    steps += if (text.isNotEmpty()) text else latex
                }
            }
        }
        val question = q.optString("text").ifBlank { q.optString("latex") }
        val answer = q.optString("finalAnswer").ifBlank { q.optString("finalAnswerLatex") }
        if (question.isBlank() && answer.isBlank()) {
            throw PhotoSolveException(40004, "没有识别到题目内容")
        }
        return PhotoSolveResult(
            recognizedQuestion = question,
            steps = steps,
            finalAnswer = answer,
        )
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()

        /** 服务端要读 5MB 以内的图，给足读写时间；模型侧超时由服务端控制。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun defaultMessage(errno: Int): String = when (errno) {
            40001 -> "图片不够清晰，建议重拍"
            40002 -> "没有识别到题目，请重新对准题目"
            40003 -> "图片过大或格式不支持"
            40000 -> "请求参数有误"
            42901 -> "今日识别额度已用完"
            50002 -> "服务端未配置解题模型"
            else -> "识别服务暂时不可用，请重试"
        }
    }
}
