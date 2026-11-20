package com.openapi.feature.video.screening

/**
 * 对应设计稿流水线里的 `CommentWindowStore`：`TimedCommentSource ─→ CommentWindowStore ─→ DanmakuOverlay`。
 *
 * 它把「来源」和「渲染」隔开，只做三件事：
 * - 按播放位置**分窗拉取**并缓存，同一窗口不重复请求（横竖屏重建、暂停时都不重新发请求）；
 * - 自己发出的弹幕**立刻回显**，带发送中 / 已发送 / 失败可重试，重试沿用同一个幂等 ID；
 * - seek 换代数：旧代在途的回包回来直接丢弃，不会出现「拖完进度条旧位置的弹幕冒出来」。
 *
 * 不含 Compose：状态是一个不可变快照 [DanmakuState]，变化通过 [onChange] 往外报，
 * 所以可以像 `planLanes` 那样直接跑 JVM 单测。调用方（页面）在主线程的 scope 里串行使用它。
 */
internal class DanmakuFeed(
    val source: TimedCommentSource,
    /** 一个窗口覆盖多少毫秒播放时间。 */
    val windowMs: Long = WINDOW_MS,
) {

    /** 渲染只读这个快照；不原地改列表，免得每帧重新排版。 */
    var state: DanmakuState = DanmakuState()
        private set

    /** 状态变了就回调一次，页面在这里把新快照接进 Compose。 */
    var onChange: (() -> Unit)? = null

    /** 已经取过的窗口：窗口起点 → 这个窗口里的弹幕。 */
    private val windows = HashMap<Long, List<TimedComment>>()

    /** 当前窗口代数，[seek] 时递增。 */
    private var generation = 0

    /** 还没取过的窗口起点，用来避免同一窗口并发重复请求。 */
    private val inFlight = HashSet<Long>()

    private var nextEchoId = -1

    /**
     * 播放位置驱动的取数：只取「包含 [atMs] 的窗口 + 后一个窗口」，
     * 已经在缓存或正在请求的窗口直接跳过。位置停在同一窗口里不会重复请求。
     */
    suspend fun sync(atMs: Long) {
        val generationOfCall = generation
        val first = atMs.floorDiv(windowMs) * windowMs
        // 后移一整窗是「往前播不卡」，前一窗是「小幅回拖不重新拉」；seek 会清缓存重来
        for (index in 0..PREFETCH_WINDOWS) {
            val start = first + index * windowMs
            if (start in windows || start in inFlight) continue
            inFlight.add(start)
            publish(state.copy(loading = true))
            val window = runCatching { source.loadWindow(start, start + windowMs, null) }
            val fetched = window.getOrNull()?.comments
            inFlight.remove(start)
            // 期间 seek 过：这一批属于旧位置，回来也不能用
            if (generationOfCall != generation) continue
            if (fetched == null) {
                publish(state.copy(loading = inFlight.isNotEmpty(), error = window.exceptionOrNull()?.message ?: "弹幕加载失败"))
                continue
            }
            windows[start] = fetched
            publish(state.copy(loading = inFlight.isNotEmpty(), error = null))
        }
    }

    /** seek：清掉窗口缓存并递增代数，让在途的旧回包作废。 */
    fun seek(atMs: Long) {
        generation++
        windows.clear()
        inFlight.clear()
        publish(state.copy(loading = false, error = null))
    }

    /**
     * 发一条弹幕。回显先落地（[SendState.Sending]），再等证据源定稿：
     * 成功后换成服务端正式 id，失败保留这条并允许 [retry] 同一个幂等 ID。
     */
    suspend fun send(atMs: Long, text: String): OutgoingComment {
        val clientMessageId = "${source.timelineKey}#${++clientSequence}"
        val echo = OutgoingComment(clientMessageId, nextEchoId--, atMs, text.trim(), SendState.Sending)
        upsert(echo)
        return finish(echo.clientMessageId) { source.post(clientMessageId, atMs, text) }
    }

    /** 重试自己发失败的那条：复用原来的 clientMessageId，服务端据此去重，不会重复上屏。 */
    suspend fun retry(clientMessageId: String): OutgoingComment? {
        val outgoing = state.outgoing.firstOrNull { it.clientMessageId == clientMessageId } ?: return null
        upsert(outgoing.copy(state = SendState.Sending, error = null))
        return finish(clientMessageId) { source.post(clientMessageId, outgoing.atMs, outgoing.text) }
    }

    /** 撤回自己发出的那条：先本地移除，再告诉来源；来源失败也不重新贴回去，刷新以服务端为准。 */
    suspend fun retract(clientMessageId: String): Boolean {
        val outgoing = state.outgoing.firstOrNull { it.clientMessageId == clientMessageId } ?: return false
        val serverId = outgoing.serverId ?: run {
            upsert(outgoing.copy(state = SendState.Retracted))
            return false
        }
        val result = runCatching { source.retract(serverId) }
        upsert(outgoing.copy(state = SendState.Retracted, error = result.exceptionOrNull()?.message))
        return result.isSuccess
    }

    /** 把一次发送的结果写回快照。 */
    private suspend fun finish(clientMessageId: String, request: suspend () -> Result<TimedComment>): OutgoingComment {
        val current = state.outgoing.first { it.clientMessageId == clientMessageId }
        // Result.Failure 在 stdlib 里是 internal，不能当 when 分支匹配，用 fold
        val next = request().fold(
            onSuccess = { saved ->
                current.copy(state = SendState.Sent, serverId = saved.id, atMs = saved.atMs, text = saved.text, error = null)
            },
            onFailure = { cause ->
                current.copy(state = SendState.Failed, error = cause.message ?: "发送失败")
            },
        )
        upsert(next)
        return next
    }

    private fun upsert(outgoing: OutgoingComment) {
        val others = state.outgoing.filterNot { it.clientMessageId == outgoing.clientMessageId }
        publish(state.copy(outgoing = others + outgoing))
    }

    private fun publish(next: DanmakuState) {
        // 渲染列表 = 已取到的窗口 + 自己的弹幕。自己那条在窗口数据带回来之前就靠回显显示，
        // 带回来之后按 id 去重，所以确认前后都不会出现同一句画两遍或中间闪没。
        val cached = windows.values.flatten()
        val cachedIds = cached.map { it.id }.toSet()
        val mine = next.outgoing
            .filter { it.state != SendState.Retracted }
            .map { TimedComment(it.serverId ?: it.echoId, it.atMs, it.text, author = "我") }
            .filter { it.id !in cachedIds }
        state = next.copy(comments = (cached + mine).sortedBy { it.atMs })
        onChange?.invoke()
    }

    /** 只影响 clientMessageId 的生成，幂等性靠它而不是时间戳。 */
    private var clientSequence = 0

    companion object {
        /** 一个窗口 8 秒：比一条弹幕走完屏幕的时间（6.5s）略长，一个窗口至少覆盖一整条。 */
        const val WINDOW_MS = 8_000L

        /** 当前窗口之外还预取几窗。 */
        const val PREFETCH_WINDOWS = 1
    }
}

/** 一个时刻弹幕的整体状态。[comments] 是给渲染用的合并结果，[outgoing] 是给面板显示发送状态用的。 */
internal data class DanmakuState(
    val comments: List<TimedComment> = emptyList(),
    val outgoing: List<OutgoingComment> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
