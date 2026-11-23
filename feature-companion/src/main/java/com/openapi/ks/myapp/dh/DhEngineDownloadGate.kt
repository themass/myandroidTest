package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.AssetManifest
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
 * 纯决策。[available] 是引擎当下的自判可用（实时查注册表，不用菜单行的旧快照），
 * [switches] 来自菜单行（available || needsDownload）。
 * 实时可用优先直接换舞台（菜单快照再旧也不重复下载）；否则行可点就走下载，
 * 不可点只显示原因。
 */
fun decideEngineClick(available: Boolean, switches: Boolean): EngineClickAction = when {
    available -> EngineClickAction.SELECT
    switches -> EngineClickAction.NOT_READY_START_DOWNLOAD
    else -> EngineClickAction.UNAVAILABLE_REASON
}

/** 引擎对应的 AssetStore 素材包；没有素材的引擎（Simple）返回 null，不走下载闸门。 */
fun DhEngineId.assets(): EngineAssets? = when (this) {
    DhEngineId.SIMPLE -> null
    DhEngineId.DUIX -> EngineAssets.DUIX
    DhEngineId.LIVE2D -> EngineAssets.LIVE2D
    DhEngineId.TAO_AVATAR -> EngineAssets.TAO
    DhEngineId.VRM -> EngineAssets.VRM
}

/**
 * 通话页引擎切换前的下载闸门。不依赖 Android：可用性判断与下载都由构造参数注入
 * （生产里 availability 查 DhEngineRegistry，ensure 是共享 AssetStore.ensure）。
 *
 * 就绪以引擎自判为准，不是 AssetStore.isReady：素材可能由 adb 推送/脚本播种
 * （Live2D 清单故意留空，isReady 永远 false），而下完素材 native 仍可能缺失——
 * 调用方必须在 download 成功后重查可用性再决定换不换舞台（CompanionRoute）。
 *
 * [download] 的 `onProgress` 由 [ensure] 的调用线程决定（AssetStore 在 IO 线程回调），
 * UI 侧要自己切回主线程。
 */
class DhEngineDownloadGate(
    private val availability: (DhEngineId) -> Boolean,
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
        val available = assets == null || availability(id)
        if (available) return EngineClickAction.SELECT
        if (!row.switches) return EngineClickAction.UNAVAILABLE_REASON
        // 清单为空：ensure() 必失败，直接提示 seed，不假装「正在下载」。
        if (assets?.let { !AssetManifest.hasDownloadablePacks(it) } == true) {
            return EngineClickAction.UNAVAILABLE_REASON
        }
        return EngineClickAction.NOT_READY_START_DOWNLOAD
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
