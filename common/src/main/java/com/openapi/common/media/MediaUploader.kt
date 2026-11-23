package com.openapi.common.media

import android.content.Context
import android.net.Uri
import com.openapi.common.image.ImageCompressor
import com.openapi.common.image.ImageUtils
import com.openapi.common.util.log.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 上传/换签名 URL 失败。
 *
 * [serverBody] 只在**服务端按自己的错误协议回了响应体**时有值（非 2xx）。调用方要把它连同
 * [status] 一起拿去翻译成自己那套人话（`/v1` 的话术表在 feature-chat 侧）；
 * 客户端本地判定的错误（未登录、响应不是 JSON、缺 upload_id）body 为 null，
 * 此时 [message] 就是可以直接给用户看的话。
 *
 * [status] 恒有值：本地判定的错误也带一个明确的状态（未登录记 401，与服务端拒绝同形）。
 */
class MediaUploadException(
    message: String,
    val status: Int,
    val code: String? = null,
    val serverBody: String? = null,
) : Exception(message)

/**
 * 图片上传的三个动作提公到一处：POST 原始字节拿 `upload_id`、按 id 换签名 GET URL、
 * 以及把选择器给的 uri 压成本地待传文件（[MediaStager]）。
 *
 * 抽出来的原因：聊天（feature-chat）和陪伴（feature-companion）打的是**同一套** `/v1/uploads`，
 * 原来那份实现长在 `AgentChatRemote` 里，companion 要用只能再抄一遍——抄两遍的东西早晚不一致。
 * 请求形状与 `/v1` 服务端一致：`routers/uploads.py` 直接读 `request.body()`，收的是原始字节而不是 multipart。
 *
 * 两条同步/异步入口是刻意的：[uploadBlocking]/[signedUrlBlocking] 给本来就在 IO 线程里跑的同步调用方
 * （`AgentChatRemote` 就是这个形状），[upload]/[signedUrl] 给协程调用方（内部切 [Dispatchers.IO]）。
 * 两者行为逐行相同，只是执行上下文不同。
 *
 * **签名 URL 是 bearer secret**：不打印、不进日志、不落缓存（生命周期只有几百秒，缓存等于给用户留坏图）。
 */
class MediaUploader(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * 上传一张图片，返回 `upload_id`（发消息时引用它）。
     *
     * @throws MediaUploadException 未登录、服务端拒绝、响应不是 JSON、或响应里没有 upload_id
     */
    @Throws(MediaUploadException::class)
    suspend fun upload(file: File, mime: String): String = withContext(Dispatchers.IO) { uploadBlocking(file, mime) }

    /** [upload] 的同步入口：调用方已经在一个阻塞线程上（不要在主线程上调）。 */
    @Throws(MediaUploadException::class)
    fun uploadBlocking(file: File, mime: String): String {
        val token = requireToken()
        val request = Request.Builder()
            .url(url("/uploads"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", mime)
            .post(file.readBytes().toRequestBody(mime.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            // message 只是兜底话（"上传失败（HTTP 401）"）：真正给人看的话术由调用方按
            // serverBody 翻译（chat 侧的 /v1 话术表）。缺了 body 也不至于抛出一个没有 message 的异常。
            if (!response.isSuccessful) throw MediaUploadException("上传失败（HTTP ${response.code}）", response.code, serverBody = text)
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw MediaUploadException("上传响应格式不对", response.code)
            val id = json.optString("upload_id", "")
            if (id.isEmpty()) throw MediaUploadException("上传没有返回 upload_id", response.code)
            return id
        }
    }

    /**
     * 按 upload_id 换一个**短期签名 GET URL**（服务端给 300 秒）。
     *
     * 任何拿不到 url 的情况都只返回 null（未登录、404、响应体坏了、网络断了）：
     * 调用方是"一张图换不到 URL 就退化成空路径"的语义，不能让一张图把整页历史拖垮。
     */
    suspend fun signedUrl(uploadId: String): String? = withContext(Dispatchers.IO) { signedUrlBlocking(uploadId) }

    /** [signedUrl] 的同步入口：调用方已经在一个阻塞线程上（不要在主线程上调）。 */
    fun signedUrlBlocking(uploadId: String): String? {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return null
        val request = Request.Builder()
            .url(url("/uploads/$uploadId"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 图换不到 URL 会静默退化成空路径，没这行日志就只能干看着不知道是服务端拒了还是没登录。
                    // 只记状态码：响应体与 URL 都可能有签名之类的东西，不进日志。
                    LogUtil.w(TAG, "换签名 URL 失败 status=${response.code} uploadId=$uploadId")
                    return@use null
                }
                // 只取 url：坏 JSON、缺字段、空串都当"没拿到"，不为这个报错
                JSONObject(response.body?.string().orEmpty()).optString("url").takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + "/v1" + path

    private fun requireToken(): String =
        tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw MediaUploadException("未登录", status = 401, code = "unauthorized")

    companion object {
        private const val TAG = "MediaUploader"

        /** REST 调用：超时明确，避免界面无限转圈（与 `AgentChatRemote.restClient` 同款参数）。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * 把选择器返回的 `content://` 图片转成应用私有目录里**可以直接上传**的本地文件。
 *
 * 复制而不是保存原始 uri：content uri 的读权限随选择器会话失效，重启后打不开。
 * 统一走 [ImageCompressor]（长边 1600、JPEG 82、800 KB 以内），不原样上传原图。
 *
 * 读不出图、写不进盘只返回 null：调用方按"没准备好"提示重试。这与 feature-chat 的
 * `AttachmentStager` 是刻意的差异——那边把失败细分成"因超限被拒"（附一句给用户看的原因）
 * 和"读取失败"，因为选择器要在**选中那一刻**告诉用户为什么不行；陪伴侧只在发送前用一次，
 * null 足够，不需要那套原因字符串。
 */
object MediaStager {

    /**
     * 解码（按 EXIF 方向摆正）→ 压缩 → 写到 [dir] 下的 `img_[stamp].jpg`，返回落盘的文件。
     * 失败返回 null，不抛。
     */
    fun stageImage(context: Context, uri: Uri, dir: File, stamp: Long): File? {
        val decoded = ImageUtils.decodeUriApplyingExifOrientation(context, uri) ?: return null
        val result = ImageCompressor.compress(decoded)
        dir.mkdirs()
        val file = File(dir, "img_$stamp.jpg")
        return runCatching { file.writeBytes(result.bytes); file }.getOrNull()
    }
}
