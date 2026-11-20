package com.openapi.feature.video.screening

import kotlinx.coroutines.delay

/**
 * 弹幕的数据来源。渲染不关心目帘从哪来：本地样例源和（将来的）服务端源实现同一个接口，
 * 共用 [DanmakuFeed] 的分窗缓存和 [DanmakuOverlay] 的渲染。
 * 接口形状照 `docs/design/media-suite.md`「通用弹幕」一节：
 * `loadWindow(startMs,endMs,cursor)`、`post(clientMessageId,atMs,text)`、`retract(id)`。
 *
 * 实现不保证线程安全：[DanmakuFeed] 在主线程的 scope 里串行调用它，
 * 真服务端实现自己在内部处理并发。
 */
internal interface TimedCommentSource {

    /** 这条时间轴的身份。同一内容的不同 URL 共用时间轴，不同剪辑默认不混。 */
    val timelineKey: TimelineKey

    /** 拉 `[startMs, endMs)` 这个窗口的一页；[cursor] 非空就接着上一页取。 */
    suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow

    /**
     * 发一条弹幕。[clientMessageId] 由客户端生成，重试时复用同一个，服务端据此去重。
     * 成功返回服务端定稿的那条（含正式 id），失败返回 failure。
     */
    suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment>

    /** 撤回自己发过的一条。只有作者或审核方有权，权限由服务端判。 */
    suspend fun retract(serverId: Int): Result<Unit>
}

/** 面板里来源的取值：0 本地样例，1 假服务端（接口和真服务端一致，随时可换成真实现）。 */
internal const val DANMAKU_SAMPLE = 0
internal const val DANMAKU_SERVER = 1

/**
 * 时间轴身份：`namespace + contentId + editionId`。
 * 本地导入的内容走 `local` 私有命名空间，MVP 不上传 URI、不跨用户共享。
 */
internal data class TimelineKey(val namespace: String, val contentId: String, val editionId: String = "v1") {
    override fun toString(): String = "$namespace:$contentId:$editionId"

    companion object {
        fun local(contentId: String) = TimelineKey("local", contentId)
    }
}

/** 一个时间窗里取回来的一页弹幕。 */
internal data class CommentWindow(
    val startMs: Long,
    val endMs: Long,
    val comments: List<TimedComment>,
    /** 还有下一页时的游标；null 表示这个窗口已经取完。 */
    val nextCursor: String? = null,
)

/** 自己发出的那条弹幕的状态，面板上直接显示。 */
internal enum class SendState { Sending, Sent, Failed, Retracted }

/** 自己发出的弹幕：回显、发送状态、重试、撤回都挂在这条记录上。 */
internal data class OutgoingComment(
    /** 客户端生成的幂等 ID，重试沿用，服务端据此去重。 */
    val clientMessageId: String,
    /** 回显那条的 id，成功后会被服务端 id 取代。 */
    val echoId: Int,
    val atMs: Long,
    val text: String,
    val state: SendState,
    /** 服务端给的 id，撤回要用；还没发出去时为 null。 */
    val serverId: Int? = null,
    /** 失败原因，面板里显示。 */
    val error: String? = null,
)

/**
 * 本地样例弹幕。故意排成「同一秒里两三条一起出来」，用来验证车道不会互相压住；
 * 也放了几条长句，用来看宽度估算和出屏节奏是否正常。
 */
internal val sampleTimeline: List<TimedComment> = listOf(
    TimedComment(1, 400, "这个开场真舒服"),
    TimedComment(2, 900, "前面别快进"),
    TimedComment(3, 1400, "山脊线太好看了"),
    TimedComment(4, 1500, "这一段配乐是谁做的"),
    TimedComment(5, 2600, "这一幕太美了"),
    TimedComment(6, 3100, "截图当壁纸了"),
    TimedComment(7, 3600, "云在动，人不动"),
    TimedComment(8, 4200, "我也是从推荐里点进来的"),
    TimedComment(9, 4800, "感觉风是凉的"),
    TimedComment(10, 5600, "这里就该停下来看一会儿"),
    TimedComment(11, 6000, "高海拔的天气真的说变就变"),
    TimedComment(12, 6400, "绿色好干净"),
    TimedComment(13, 7300, "已经循环第三遍了"),
    TimedComment(14, 7900, "有没有人注意到右边那条小径"),
    TimedComment(15, 8600, "取景真的很克制"),
    TimedComment(16, 9400, "这句旁白好听"),
    TimedComment(17, 10200, "看完想去山里"),
    TimedComment(18, 11000, "谢谢你拍下来"),
)

/**
 * 本地样例源：完全不联网，永远可用，也是没有服务端时的默认选择。
 * 它同样支持发送和撤回，所以发送链路在没有服务端时也能整机演示，只是数据只留在本机内存里。
 */
internal class SampleDanmakuSource(
    override val timelineKey: TimelineKey = TimelineKey("local", "sample"),
    private val timeline: List<TimedComment> = sampleTimeline,
) : TimedCommentSource {

    private val sent = ArrayList<TimedComment>()
    private var nextId = (timeline.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow =
        CommentWindow(
            startMs,
            endMs,
            (timeline + sent).filter { it.atMs in startMs until endMs }.sortedBy { it.atMs },
        )

    override suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("弹幕不能为空"))
        val comment = TimedComment(nextId++, atMs, trimmed, author = "我")
        sent.add(comment)
        return Result.success(comment)
    }

    override suspend fun retract(serverId: Int): Result<Unit> {
        sent.removeAll { it.id == serverId }
        return Result.success(Unit)
    }
}

/**
 * 假的服务端源：把服务端源的接口形状和边界行为固定下来，先用它把「真实收发」这条链路跑通，
 * 真服务端上线时只替换这一个实现，[DanmakuFeed] 和渲染都不用动。
 *
 * 覆盖的行为：分窗 + 游标分页、按 `clientMessageId` 幂等、文本长度校验、撤回标记
 * （撤回后再次拉取就不返回，对应「刷新收到撤回标记即移除」），以及可注入的失败。
 *
 * @param latencyMs 模拟网络往返；测试里传 0。
 * @param pageSize 一页多少条，用来真的走一遍游标分页。
 * @param failPost 置 true 后 post 直接失败，用来看「失败可重试」。
 */
internal class InMemoryDanmakuSource(
    override val timelineKey: TimelineKey,
    seed: List<TimedComment> = sampleTimeline,
    private val latencyMs: Long = 240L,
    private val maxTextLength: Int = 40,
    private val pageSize: Int = 200,
    var failPost: Boolean = false,
) : TimedCommentSource {

    private val stored = ArrayList(seed)
    private val idsByClientMessage = HashMap<String, Int>()
    private var nextId = (seed.maxOfOrNull { it.id } ?: 0) + 1

    /** 当前「服务端」上的内容，测试和面板状态检查用。 */
    val storedSnapshot: List<TimedComment> get() = stored.toList()

    override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow {
        if (latencyMs > 0) delay(latencyMs)
        val inRange = stored.filter { it.atMs in startMs until endMs }.sortedBy { it.atMs }
        // 游标就是「已经取到第几条」，真服务端会是服务端自己的分页 token
        val skip = cursor?.toIntOrNull() ?: 0
        val page = inRange.drop(skip).take(pageSize)
        val taken = skip + page.size
        return CommentWindow(startMs, endMs, page, nextCursor = if (taken < inRange.size) taken.toString() else null)
    }

    override suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment> {
        if (latencyMs > 0) delay(latencyMs)
        if (failPost) return Result.failure(IllegalStateException("网络不可用"))
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("弹幕不能为空"))
        if (trimmed.length > maxTextLength) {
            return Result.failure(IllegalArgumentException("最多 $maxTextLength 个字"))
        }
        // 幂等：同一个 clientMessageId 重试多次，服务端只有一条
        idsByClientMessage[clientMessageId]?.let { id ->
            return Result.success(stored.first { it.id == id })
        }
        val comment = TimedComment(nextId++, atMs, trimmed, author = "我")
        stored.add(comment)
        idsByClientMessage[clientMessageId] = comment.id
        return Result.success(comment)
    }

    override suspend fun retract(serverId: Int): Result<Unit> {
        if (latencyMs > 0) delay(latencyMs)
        if (stored.none { it.id == serverId }) return Result.failure(IllegalStateException("这条已经不在时间轴上了"))
        stored.removeAll { it.id == serverId }
        return Result.success(Unit)
    }
}
