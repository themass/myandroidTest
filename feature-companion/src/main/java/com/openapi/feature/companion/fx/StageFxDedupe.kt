package com.openapi.feature.companion.fx

/**
 * 同 assetId 去重（纯函数，JVM 钉死）。
 *
 * **不是事件总线**——只是既有 `gestureSeq` 流上的闸：同一特效 400ms 内连点合并。
 * 宿主是 LottieFxLayer 的 remember 状态，不另起 SharedFlow。
 */
enum class DedupeAction { PASS, DROP }

/** 去重窗口：同 assetId 400ms 内合并（方案 §4）。边界取严格小于——恰好 400ms 算两次。 */
const val FX_DEDUPE_WINDOW_MS = 400L

fun dedupe(lastSeenAtNanos: Long?, nowNanos: Long): DedupeAction {
    val last = lastSeenAtNanos ?: return DedupeAction.PASS
    return if (nowNanos - last < FX_DEDUPE_WINDOW_MS * 1_000_000L) {
        DedupeAction.DROP
    } else {
        DedupeAction.PASS
    }
}
