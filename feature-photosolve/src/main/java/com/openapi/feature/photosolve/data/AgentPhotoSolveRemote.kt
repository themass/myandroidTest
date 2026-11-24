package com.openapi.feature.photosolve.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Companion /v1 uses Bearer auth and plain JSON; the legacy AES/errno client cannot decode it. */
internal class AgentPhotoSolveRemote(
    baseUrl: String,
    private val tokenProvider: () -> String?,
    private val calls: Call.Factory = CLIENT,
) {
    private val endpoint = (baseUrl.trimEnd('/') + "/v1/photosolve/solve").toHttpUrl()

    suspend fun solve(image: ByteArray, requestId: String): PhotoSolveApiResponse {
        val token = tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw failure(PhotoSolveError.LOGIN_REQUIRED)
        val request = Request.Builder()
            .url(endpoint.newBuilder().addQueryParameter("client_request_id", requestId)
                .addQueryParameter("subject", "math").build())
            .header("Authorization", "Bearer $token")
            .post(image.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = calls.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(failure(PhotoSolveError.MODEL_UNAVAILABLE))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { decode(it.code, it.body?.string().orEmpty()) }
                    }
                    if (continuation.isCancelled) return
                    result.fold(continuation::resume) { error ->
                        continuation.resumeWithException(
                            error as? PhotoSolveException ?: failure(PhotoSolveError.MODEL_UNAVAILABLE),
                        )
                    }
                }
            })
        }
    }

    internal fun decode(status: Int, body: String): PhotoSolveApiResponse {
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        if (status !in 200..299) {
            val fallback = when (status) {
                401, 403 -> PhotoSolveError.LOGIN_REQUIRED
                413, 415, 422 -> PhotoSolveError.BAD_IMAGE
                429 -> PhotoSolveError.QUOTA_EXCEEDED
                else -> PhotoSolveError.MODEL_UNAVAILABLE
            }
            // Do not expose gateway exceptions, URLs, or provider payloads as UI messages.
            val code = runCatching { json?.getAsJsonObject("detail")?.get("code")?.asInt }.getOrNull()
            throw failure(if (fallback == PhotoSolveError.LOGIN_REQUIRED) fallback else code?.takeIf { it in KNOWN_CODES } ?: fallback)
        }
        try {
            require(json != null && json.get("questionFound")?.asJsonPrimitive?.isBoolean == true)
            val questions = json.getAsJsonArray("questions") ?: error("missing questions")
            if (json.get("questionFound").asBoolean) {
                require(questions.size() > 0)
                questions.forEach { item ->
                    val question = item.asJsonObject
                    require(question.string("text").isNotBlank())
                    require(question.string("finalAnswer").isNotBlank() || question.string("finalAnswerLatex").isNotBlank())
                    question.getAsJsonArray("steps")?.forEach { step ->
                        require(step.asJsonObject.get("text")?.isJsonNull != true)
                        require(step.asJsonObject.get("latex")?.isJsonNull != true)
                    }
                    // Gson bypasses Kotlin defaults: reject explicit nulls before mapping.
                    listOf("latex", "thinking", "finalAnswer", "finalAnswerLatex").forEach { key ->
                        require(question.get(key)?.isJsonNull != true)
                    }
                    require(question.get("steps")?.isJsonNull != true)
                }
            }
            return Gson().fromJson(json, PhotoSolveApiResponse::class.java)
        } catch (_: Exception) {
            throw failure(PhotoSolveError.MODEL_UNAVAILABLE)
        }
    }

    private fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private companion object {
        val CLIENT = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS).build()
        val KNOWN_CODES = setOf(40000, 40001, 40002, 40003, 42901, 42902, 50001, 50002)
        fun failure(code: Int) = PhotoSolveException(code, PhotoSolveError.userMessage(code))
    }
}
