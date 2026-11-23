package com.openapi.speech.tts

import com.openapi.speech.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 情绪 TTS（Suno Bark）客户端骨架。**默认关闭**，仅当
 * `BuildConfig.BARK_TTS_ENABLED` 为真时才被调用；关闭时本类不被实例化，
 * 现有朗读链路完全不变。
 *
 * 为什么要有这个类，而不是直接往 [ChatRepository 那条 tts.json] 里加参数：
 * Bark 是独立的 Python 服务，不在 `api.ok123find.top` 的 Java 后端里，走自己的
 * 域名与端口（见 tools/bark-tts/README.md），所以它需要自己的 base URL 和
 * 自己的开关，不能复用 `Constants.getUrl`。
 *
 * 关键约定——**情绪标记必须原样透传**：
 * `[laughs]` / `[sighs]` / `[gasps]` 不是 Bark 的特殊 token，它们和普通中文
 * 一样过 BERT tokenizer，是 text→semantic GPT 自己学会了在那里插笑、叹气。
 * 所以从 agent 输出到这里，一个字节都不能改：不转义、不去方括号、不过 Markdown
 * sanitizer。任何一步把 `[laughs]` 变成 `\[laughs]` 或被吃掉，笑声就没了。
 *
 * 时延现实：Bark 上游 README 说企业级 GPU 上接近实时，老 GPU/CPU「显著更慢」。
 * 本 PoC 的本机实测数字见 tools/bark-tts/out/latency.json（跑通后填）。在拿到
 * 实测数之前，按「生成比音频长、不能边说边合成」对待：只适合「点一条消息、听
 * 一条」的主动朗读，且 UI 要有明确的合成中状态。实时通话链路
 * （LiveKitCompanionTransport）不要接这里。
 */
object BarkEmotionalTts {

    /** 编译期开关：false 时整条链路不会被走到。 */
    val enabled: Boolean
        get() = BuildConfig.BARK_TTS_ENABLED && BuildConfig.BARK_TTS_BASE_URL.isNotBlank()

    private const val PATH = "/tts"
    private const val MIME = "application/json"

    /**
     * Bark 一次调用最多 768 个 semantic token，@ 49.9 Hz ≈ 15.4 秒音频
     * （bark/generation.py:447），再长会被静默截断。客户端先按这个上限拦一道，
     * 比服务端悄悄截掉好。
     */
    private const val MAX_CHARS = 120

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 生成比实时慢，读超时给到分钟级；连接超时照常。
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 合成一段带情绪标记的语音。
     *
     * @param text 待朗读文本，**包含** `[laughs]` 等标记，原样发送。
     * @param speaker Bark 的 voice prompt 名，如 `v2/zh_speaker_3`。
     * @return 可直接交给 `AudioPlayer.play` 的 `file://` 地址；失败返回 null，
     *         由调用方回退到现有后端 TTS 或设备 TTS。
     */
    suspend fun synthesizeToFile(
        text: String,
        speaker: String,
        cacheDir: File,
    ): String? {
        if (!enabled) return null
        val body = text.trim()
        if (body.isEmpty()) return null

        val req = Request.Builder()
            .url(BuildConfig.BARK_TTS_BASE_URL.trimEnd('/') + PATH)
            .post(buildJson(body, speaker).toRequestBody(MIME.toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                val dir = File(cacheDir, "bark").apply { mkdirs() }
                val file = File(dir, "bark_${System.currentTimeMillis()}.wav")
                file.writeBytes(bytes)
                "file://" + file.absolutePath
            }
        } catch (_: Exception) {
            // PoC 服务不保证可用性；任何异常都退回现有链路，不要在朗读路径上抛。
            null
        }
    }

    private fun buildJson(text: String, speaker: String): String {
        val trimmed = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text
        return JSONObject()
            .put("text", trimmed)
            .put("speaker", speaker)
            .toString()
    }
}
