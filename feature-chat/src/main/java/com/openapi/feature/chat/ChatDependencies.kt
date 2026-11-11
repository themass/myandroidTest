package com.openapi.feature.chat

import com.openapi.feature.chat.domain.auth.LoginGate
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.feature.chat.domain.speech.SpeechToText

/**
 * 依赖倒置接入点。
 *
 * `:features:chat` 不能反向依赖 `:app`，而后端协议实现（form 提交、AES 解密、UA 签名）、
 * greenDAO 存储、以及字节语音引擎的封装都在 app 里。所以由 app 在 Application.onCreate
 * 时注入实现，本模块只依赖 [ChatRepository] / [SpeechToText] 这些抽象。
 *
 * 未注入时调用 [require] 会抛出明确错误，而不是在 UI 层莫名其妙地空指针；
 * 语音识别是可选项，未注入时 [speechToText] 返回 null，界面降级为"只发语音不转写"。
 */
object ChatDependencies {

    @Volatile
    private var repository: ChatRepository? = null

    @Volatile
    private var speechToText: SpeechToText? = null

    @Volatile
    private var loginGate: LoginGate? = null

    fun install(repository: ChatRepository) {
        this.repository = repository
    }

    fun installLoginGate(loginGate: LoginGate) {
        this.loginGate = loginGate
    }

    fun loginGate(): LoginGate = checkNotNull(loginGate) {
        "LoginGate 未注入：请在 Application.onCreate 中调用 ChatDependencies.installLoginGate(...)"
    }

    fun installSpeechToText(speechToText: SpeechToText) {
        this.speechToText = speechToText
    }

    fun require(): ChatRepository = checkNotNull(repository) {
        "ChatRepository 未注入：请在 Application.onCreate 中调用 ChatDependencies.install(...)"
    }

    fun speechToText(): SpeechToText? = speechToText
}
