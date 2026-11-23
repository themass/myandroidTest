package com.openapi.feature.video.screening

/**
 * 服务端字幕条目的映射与校验规则——全是纯函数，跑 JVM 单测。
 *
 * 服务端（testserverV）把字幕整份文本放在 JSON 信封里下发（而不是裸 .srt 文件），
 * 因为 app 的现有网络层对 .json 响应统一做 AES 解密（密钥来自请求 User-Agent），
 * 走非 JSON 通道就得另配一套解密。所以这里校验的是「JSON 字段里的字幕文本」。
 */

/** 一条服务端字幕（已经映射好展示文案）。 */
internal data class SubtitleItem(
    val id: Long,
    val title: String,
    /** 语言中文名（zh→中文），未知语言原样显示。 */
    val langLabel: String,
    /** 大写格式名：SRT / VTT。 */
    val formatLabel: String,
    /** 人类可读大小，如 12 KB；未知为空串。 */
    val sizeLabel: String,
) {
    val label: String get() = listOf(langLabel, formatLabel, sizeLabel).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** 语言代码 → 界面文案；没登记的代码原样显示，不瞎猜。 */
internal fun subtitleLangLabel(lang: String?): String = when (lang?.trim()?.lowercase()) {
    null, "" -> "其他语言"
    "zh", "cn", "chi", "zho", "zh-cn", "zh-hans" -> "中文"
    "zh-tw", "zh-hant" -> "繁体中文"
    "en", "eng" -> "英文"
    "ja", "jpn" -> "日文"
    "ko", "kor" -> "韩文"
    else -> lang.trim()
}

/**
 * 字幕格式：优先服务端 format 字段，其次从文件名后缀猜，最后默认 SRT
 * （SRT 是最大公约数；解析器对两者都支持，猜错的代价只是文案不准）。
 */
internal fun subtitleFormat(format: String?, title: String?): String {
    val declared = format?.trim()?.lowercase()
    return when {
        declared == "vtt" || declared == "webvtt" -> "VTT"
        declared == "srt" || declared == "subrip" -> "SRT"
        declared.isNullOrEmpty() && title.endsWithIgnoreCase(".vtt") -> "VTT"
        else -> "SRT"
    }
}

private fun String?.endsWithIgnoreCase(suffix: String): Boolean =
    this != null && this.length >= suffix.length && this.endsWith(suffix, ignoreCase = true)

/** 字节数 → 界面文案。 */
internal fun subtitleSizeLabel(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/**
 * 这份文本像不像能解析的字幕。
 * 只挡明显的拿错文件（HTML 报错页、空文件、二进制乱码），格式细节交给 LocalSubtitleTrack 的严格解析。
 */
internal fun looksLikeSubtitle(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val head = text.take(4096)
    if (head.trimStart().startsWith("<")) return false
    // VTT 有签名头；SRT 认时间轴箭头（形如 00:00:01,000 --> 00:00:04,000）
    return head.startsWith("WEBVTT") || Regex("""-->\s*\d{2}:""").containsMatchIn(head)
}

/** 服务端 id 藏在 RemoteEntry.key 的 `srv:` 前缀后面；不是服务端条目就返回 null。 */
internal fun serverIdOf(entryKey: String): String? =
    entryKey.takeIf { it.startsWith(SERVER_KEY_PREFIX) }?.removePrefix(SERVER_KEY_PREFIX)?.takeIf { it.isNotBlank() }
