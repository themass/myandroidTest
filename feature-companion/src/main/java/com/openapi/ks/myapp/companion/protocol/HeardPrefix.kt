package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * 打断时「已精确播出的文本前缀」（超越 OLLMV 第二刀 C2）。
 *
 * OLLMV 在打断时回传 `interrupt-signal {text: fullResponse}`——**全量**文本，
 * 因为浏览器播放器量不出播到了哪。我方相反：端侧有精确的播放进度
 * （`SpeechTimeline.spokenChars` 按 `t0` + 段时长把「说出口了几个字」钉死），
 * 所以回传的是切到字幕流上的**精确前缀**：服务端 memory 拿它改写上一条回复
 * （heard + 「[Interrupted by user]」），下一轮 LLM 读到的是她真说过的话。
 *
 * 契约全文：`docs/design/2026-11-28-heard-prefix-feedback-contract.md`。
 */
object HeardPrefix {

    /**
     * `heard_text` 的 UTF-8 预算。信封上限 4096 B（`ProtocolCodec.MAX_PAYLOAD_BYTES`），
     * 骨架字段（speech_id / played_ms / reason / 信封）占几十到一百字节；给前缀留 1500 B，
     * 超了**只裁前缀不裁整条**——这是「禁静默无效」的一侧：打断必须发出去，
     * 前缀降级成截断版并带 `heard_truncated` 标记。
     */
    const val MAX_HEARD_BYTES = 1_500

    /**
     * 按**码点**切文本前缀。`chars` 与 `speech.seg.chars` 同口径（服务端 Python `len()`
     * 是码点）；按 UTF-16 code unit 切会把 emoji 劈成半个代理对，服务端收到的就是坏文本。
     *
     * [chars] 超长按全量给（delta 流落后于音频时宁可少说没送的，绝不编字——
     * ViewModel 的 `truncateStreaming` 对气泡做的是同一件事）。
     */
    fun slice(text: String, chars: Int): String {
        if (chars <= 0 || text.isEmpty()) return ""
        var remaining = chars
        var i = 0
        while (i < text.length && remaining > 0) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            remaining--
        }
        return text.substring(0, i)
    }

    /** UTF-8 字节数（预算判定用，与服务端收到这条 JSON 后按 UTF-8 编码的长度一致）。 */
    fun utf8Len(s: String): Int = s.toByteArray(StandardCharsets.UTF_8).size

    /**
     * 把前缀压进 [maxBytes]：按码点累加，不劈半个字符。
     */
    fun fitUtf8(s: String, maxBytes: Int): String {
        if (utf8Len(s) <= maxBytes) return s
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val cpLen = utf8Len(String(Character.toChars(cp)))
            if (bytes + cpLen > maxBytes) break
            bytes += cpLen
            i += Character.charCount(cp)
        }
        return s.substring(0, i)
    }

    /**
     * `control.barge_in` 的 args（含 heard 回传）。
     *
     * 字段与顺序契约为：
     * `{speech_id, played_ms, reason, heard_text, heard_truncated?}`——
     * `heard_text` **总是出现**（一个字没说就是空串）：服务端据此区分
     * 「这个端会回前缀」（键存在）与「老端不回报文」（键缺失）。
     * `heard_truncated` 仅在被预算裁过时出现，服务端据此知道前缀不完整、
     * 按 `played_ms` 的 est_ms 口径兜底补齐。
     */
    fun bargeArgs(
        speechId: String,
        playedMs: Long,
        reason: String,
        streamedText: String,
        spokenChars: Int,
    ): JSONObject {
        val full = slice(streamedText, spokenChars)
        val budgeted = fitUtf8(full, MAX_HEARD_BYTES)
        val args = JSONObject()
            .put("speech_id", speechId)
            .put("played_ms", playedMs)
            .put("reason", reason)
            .put("heard_text", budgeted)
        if (budgeted != full) args.put("heard_truncated", true)
        return args
    }
}
