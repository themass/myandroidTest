package com.openapi.feature.chat.domain.speech

/**
 * 语音转文字。
 *
 * `:features:chat` 不能依赖 `:app`，而字节 SpeechEngine 的封装（AsrByteDance）在 app 里，
 * 所以这里只定义能力，由 app 在 [com.openapi.feature.chat.ChatDependencies.installSpeechToText] 注入。
 *
 * 返回 null 表示识别失败或没有拿到结果——调用方保留语音气泡但不发消息，不猜测内容。
 */
interface SpeechToText {

    /**
     * 把一段录音转成文字。
     *
     * @param audioFilePath 录音文件绝对路径（m4a/wav 都接受，实现内部负责必要转码）
     * @return 识别出的文字；空串或 null 视为失败
     */
    suspend fun transcribe(audioFilePath: String): String?
}
