package com.openapi.feature.chat.data.remote

/**
 * 「上一轮还占着会话」（HTTP 409）的恢复策略：**先取消服务端那一轮，再重试一次**。
 *
 * 为什么必须有：点停止以前只杀本地 Job（[com.openapi.feature.chat.presentation.ReplyStreamer.cancel]
 * 的旧行为），服务端那一轮继续跑；用户接着发下一条就吃 409「上一轮回复还没结束」，
 * 于是变成「怎么发都失败」。端上主动补一次 cancel 把这个死结打开：上一轮被服务端正式取消，
 * 重试通常直接成功。
 *
 * **只重试一次**：409 的另一种成因是「别的设备正在跑这一轮」，那一种重试到第几次都是 409，
 * 无限重试只会把会话卡得更久。仍失败就把原异常交出去（上层按
 * [com.openapi.feature.chat.domain.model.ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING] 报明确文案）。
 *
 * [cancelTurn] 失败**不阻断重试**（取消接口挂了也要试一次——上一轮可能已经结束），
 * 原因只经 [onConflictLogged] 记日志。
 *
 * 抽成独立函数（而不是写在仓库方法里）的原因：这条策略是「回合状态机」的规矩，
 * 与 HTTP 客户端无关；注入 [isConflict] 之后它能在纯 JVM 测试里跑，不需要起 HTTP 桩。
 */
internal suspend fun <T> withTurnConflictRecovery(
    /** 认出「这就是 409」。data 层注入，本函数不认识 [AgentChatRemote.Failure] 的字段形状。 */
    isConflict: (Throwable) -> Boolean,
    cancelTurn: suspend () -> Unit,
    onConflictLogged: (Throwable) -> Unit = {},
    attempt: suspend () -> T,
): T = try {
    attempt()
} catch (e: Exception) {
    if (!isConflict(e)) throw e
    runCatching { cancelTurn() }
        .onFailure { onConflictLogged(it) }
    attempt()
}
