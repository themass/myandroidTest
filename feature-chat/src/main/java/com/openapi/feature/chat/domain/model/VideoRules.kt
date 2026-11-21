package com.openapi.feature.chat.domain.model

/**
 * 视频消息的硬规则（P2-13）：只有 MP4、不超过 25 MiB。
 *
 * 单独一个纯 Kotlin 对象，是因为这两条要在**两处**独立执行且必须完全一致：
 * - 选择器刚选完就拒（[com.openapi.feature.chat.presentation.AttachmentStager]，省流量、当场报错）；
 * - 签发上传之前再兜一道（[com.openapi.feature.chat.data.remote.AttachmentApi.uploadVideo]，
 *   防止任何没走暂存的调用把超限字节喂给服务端）。
 * 写两份常量迟早漂移成两个值，所以规则只在这里有一份。
 */
object VideoRules {

    /** 服务端对 scene `chat-video` 的硬上限：25 MiB = 26214400 字节。等于上限放行，超过才拒。 */
    const val MAX_VIDEO_BYTES = 26_214_400L

    /** 只支持 MP4：服务端不转码，其他容器（mov/mkv）进了桶也没法被播放端保证解码。 */
    fun isMp4(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").equals("mp4", ignoreCase = true)

    /** null = 可以上传；非 null = 必须在任何网络调用之前拒掉，调用方据此取对应文案。 */
    fun preflightReject(fileName: String, sizeBytes: Long): VideoReject? = when {
        !isMp4(fileName) -> VideoReject.NotMp4
        sizeBytes > MAX_VIDEO_BYTES -> VideoReject.TooLarge
        else -> null
    }
}

/** 视频被本地预检拒绝的原因（决定给用户看哪条文案）。 */
enum class VideoReject {
    NotMp4,
    TooLarge,
}
