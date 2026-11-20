package com.openapi.ks.myapp.companion.protocol

import com.openapi.ks.myapp.companion.protocol.ProtocolCodec.Envelope

/**
 * 收侧状态机：去重 → 序号 → 定向（§5.2）。
 *
 * 与服务端 `envelope.py` 里的 `Receiver` 是同一套判定，两边必须给出同样的结论，
 * 否则「重连后到底丢没丢消息」在两端会各说各话。一致性同样由共享向量锁住。
 *
 * 只管信封层，不看 `args`。这样任何一个事件处理器写错，都不会影响「下一条消息还能
 * 不能进来」——§14.1 的统一原则。
 */
class ProtocolReceiver(identity: String? = null) {

    /**
     * 我在这条连接上的 identity，用来判 `dst` 是不是指我。
     *
     * 可以改：建 `Room` 的时候还不知道自己的 identity，它要等 `connect()` 完成才由
     * SDK 分配。`null` 的语义是"我只收广播"——此时定向消息（`dst` 非空）一律
     * NOT_FOR_ME，这是保守的一侧：漏收是可见的（计数在 `gaps` 附近），收错不可见。
     */
    var identity: String? = identity
        internal set

    enum class Verdict {
        ACCEPT,
        DUPLICATE,
        STALE,
        NOT_FOR_ME,
    }

    /** 去重与序号的簿记，按 `(publisher, topic)` 拆开。 */
    class State {
        /**
         * LRU 上限（§5 规则 4：256 条或 10 秒，取先到者）。只实现条数这一半：
         * 时间那一半需要时钟注入，收益不抵复杂度——一条 cue 的生命周期远短于
         * 256 条消息。
         */
        var dedupCapacity: Int = 256

        internal val lastSeq = HashMap<Pair<String, String>, Long>()
        internal val epochs = HashMap<Pair<String, String>, String>()

        // publisher -> 有序 id 集。LinkedHashMap 保插入顺序，第一个 key 就是最老的。
        internal val seen = HashMap<String, LinkedHashSet<String>>()
        internal val seenEpochs = HashMap<String, String>()

        var gaps: Int = 0
            internal set
    }

    val state: State = State()

    /*
     * 计数（§14.3）。按 topic × 结果分开记，运维才能区分「是网络还是我的编译器」。
     * 用 var 而不是 val：这些是逐条累加的，不能让外部误以为可以重建。
     */
    var received: Int = 0
        private set
    var applied: Int = 0
        private set
    var droppedDup: Int = 0
        private set
    var droppedStale: Int = 0
        private set
    var droppedExpired: Int = 0
        private set
    var droppedUnknown: Int = 0
        private set
    var droppedUnauthorized: Int = 0
        private set
    var droppedBad: Int = 0
        private set

    /** 重连后清空某个发布者的全部簿记（§5.1）。 */
    fun forgetPublisher(publisher: String) {
        state.lastSeq.keys.removeAll { it.first == publisher }
        state.epochs.keys.removeAll { it.first == publisher }
        state.seen.remove(publisher)
        state.seenEpochs.remove(publisher)
    }

    /** `RoomEvent.Reconnected` 上调用。 */
    fun forgetAll() {
        state.lastSeq.clear()
        state.epochs.clear()
        state.seen.clear()
        state.seenEpochs.clear()
    }

    private fun remember(publisher: String, ep: String, id: String) {
        val bucket = state.seen.getOrPut(publisher) { LinkedHashSet() }
        if (state.seenEpochs[publisher] != ep) {
            // 新 epoch：旧 id 与新 id 不可比，整张表作废。
            bucket.clear()
            state.seenEpochs[publisher] = ep
        }
        bucket.remove(id)      // 重新插入，让它变成最新的
        bucket.add(id)
        while (bucket.size > state.dedupCapacity) {
            val oldest = bucket.first()
            bucket.remove(oldest)
        }
    }

    private fun isDup(publisher: String, ep: String, id: String): Boolean {
        if (state.seenEpochs[publisher] != ep) return false
        return state.seen[publisher]?.contains(id) == true
    }

    /** 按 §5 规则 3/4 与 §5.2 给一封信定性，并更新簿记。 */
    fun observe(envelope: Envelope, publisher: String, topic: String): Verdict {
        received++

        if (!envelope.isFor(identity.orEmpty())) {
            // dst 不是我：静默丢弃，不记 gap（我本来就不该看见它）。
            return Verdict.NOT_FOR_ME
        }

        if (isDup(publisher, envelope.ep, envelope.id)) {
            droppedDup++
            return Verdict.DUPLICATE
        }

        val key = publisher to topic
        val previousEp = state.epochs[key]
        var last = state.lastSeq[key]

        if (previousEp != null && previousEp != envelope.ep) {
            // 换了 epoch：新流的 seq 从 0 开始，与旧的不可比（F4）。
            last = null
        }

        if (last != null) {
            if (envelope.seq <= last) {
                droppedStale++
                remember(publisher, envelope.ep, envelope.id)
                return Verdict.STALE
            }
            if (envelope.seq > last + 1) {
                // 跳号：计数，**不请求重传**（§5.2）。表现指令的价值窗口太短，
                // 等重传的空档比丢失更伤。
                state.gaps++
            }
        } else if (envelope.seq != 0L) {
            // 流的第一条不是 0：开头就断了。同样只计数。
            state.gaps++
        }

        state.lastSeq[key] = envelope.seq
        state.epochs[key] = envelope.ep
        remember(publisher, envelope.ep, envelope.id)
        applied++
        return Verdict.ACCEPT
    }
}
