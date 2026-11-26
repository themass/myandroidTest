package com.openapi.feature.chat.domain.model

/**
 * A40：排队消息「立刻发出（插话）」的服务端裁决结果。
 *
 * `/v1` 的 submit 对回合进行中的提交给四种处置（服务端 `_SUBMIT_LUA` 的判决）：
 * - [MERGED]：模型还没出第一个字 → 这条并进当前回合，回合**原地重跑**
 *   （事件流会来 `turn.restarted`，已出的字作废）；
 * - [STEERED]：模型正在输出 → 立刻注入正在跑的回合（有额度），当前回复会看到它；
 * - [QUEUED]：回合在调工具、或插话额度用完 → 服务端自己的队列暂存，
 *   本轮结束后由**服务端**排空成新回合（客户端要接住那个回合）；
 * - [STARTED]：点插话的一瞬间当前回合恰好结束 → 这条直接开了新回合（同样要接住）。
 */
enum class SteerDisposition { MERGED, STEERED, QUEUED, STARTED }

/** [steerQueuedMessage] 的回执。[ok]=false 时 [disposition] 为 null，调用方把这条放回队列。 */
data class SteerResult(
    val ok: Boolean,
    val disposition: SteerDisposition? = null,
    val messageId: Long = 0,
    val turnId: String? = null,
)
