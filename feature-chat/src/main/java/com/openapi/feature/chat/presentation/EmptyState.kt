package com.openapi.feature.chat.presentation

/**
 * 消息区为空时该显示什么。
 *
 * 以前这里只有一个布尔判断（`if (!state.loading) EmptyChat(...)`），于是**加载失败**
 * 和**真的没有消息**长得一模一样：断网冷启动会看到一次全新的、从没聊过的空白会话，
 * 和「历史被清空」无法区分。读取失败必须单独成为一种状态，并且把原因留在界面上。
 */
internal sealed interface EmptyState {
    /** 正在读历史：先不画空状态，否则会先闪一下「还没有消息」再跳成历史。 */
    data object Loading : EmptyState

    /** 读取失败。带原因和重试入口，不能退化成空会话。 */
    data class Failed(val message: String) : EmptyState

    /** 确实没有消息（新会话）。 */
    data object Blank : EmptyState
}

internal fun emptyStateFor(state: ChatUiState): EmptyState {
    if (state.loading) return EmptyState.Loading
    state.loadError?.let { return EmptyState.Failed(it) }
    return EmptyState.Blank
}

/**
 * 把加载异常翻成一句用户能看懂的话。
 *
 * 错误原文必须留着——换成「请重试」就又把「不知道哪失败」买回来了，
 * 这和 [com.openapi.feature.chat.presentation.ui.ChatErrorBar] 是同一个原则。
 */
internal fun describeLoadFailure(error: Throwable): String {
    val raw = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "未知错误"
    return "没能读到历史：$raw"
}
