package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.EngineAssets

/** 通话页下拉里点一行后该做什么。 */
enum class EngineClickAction {
    /** 引擎可选，但素材还没下好：开始下载，先不换舞台。 */
    NOT_READY_START_DOWNLOAD,

    /** 这一行不能用：只显示原因。 */
    UNAVAILABLE_REASON,

    /** 立刻换舞台。 */
    SELECT,
}

/**
 * 纯决策。[switches] 来自菜单行（引擎自己的可用性），[ready] 表示素材已就绪。
 * 不可用优先：不可用的引擎下了素材也用不了。
 */
fun decideEngineClick(ready: Boolean, switches: Boolean): EngineClickAction = when {
    !switches -> EngineClickAction.UNAVAILABLE_REASON
    !ready -> EngineClickAction.NOT_READY_START_DOWNLOAD
    else -> EngineClickAction.SELECT
}

/** 引擎对应的 AssetStore 素材包；没有素材的引擎（Simple）返回 null，不走下载闸门。 */
fun DhEngineId.assets(): EngineAssets? = when (this) {
    DhEngineId.SIMPLE -> null
    DhEngineId.DUIX -> EngineAssets.DUIX
    DhEngineId.LIVE2D -> EngineAssets.LIVE2D
    DhEngineId.TAO_AVATAR -> EngineAssets.TAO
}

/**
 * 通话页引擎切换前的下载闸门。不依赖 Android：就绪判断与下载都由构造参数注入
 * （生产里是共享的 AssetStore.isReady / AssetStore.ensure）。
 *
 * [download] 的 `onProgress` 由 [ensure] 的调用线程决定（AssetStore 在 IO 线程回调），
 * UI 侧要自己切回主线程。
 */
class DhEngineDownloadGate(
    private val isReady: (EngineAssets) -> Boolean,
    private val ensure: suspend (EngineAssets, (Float) -> Unit) -> Result<Unit>,
) {
    sealed interface Outcome {
        data object Success : Outcome
        data object Busy : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private val inFlight = mutableSetOf<DhEngineId>()

    fun actionFor(row: DhEngineMenuRow): EngineClickAction {
        val id = row.id ?: return EngineClickAction.SELECT
        val assets = id.assets()
        val ready = assets == null || isReady(assets)
        return decideEngineClick(ready = ready, switches = row.switches)
    }

    fun isDownloading(id: DhEngineId): Boolean = synchronized(inFlight) { id in inFlight }

    /** 同一个引擎同时只下载一次；重复点击返回 [Outcome.Busy]。 */
    suspend fun download(id: DhEngineId, onProgress: (Float) -> Unit): Outcome {
        val assets = id.assets() ?: return Outcome.Success
        val started = synchronized(inFlight) { inFlight.add(id) }
        if (!started) return Outcome.Busy
        try {
            val result = ensure(assets, onProgress)
            return result.fold(
                onSuccess = { Outcome.Success },
                onFailure = { e -> Outcome.Failed(e.message?.takeIf { it.isNotBlank() } ?: "下载失败") },
            )
        } finally {
            synchronized(inFlight) { inFlight.remove(id) }
        }
    }
}
