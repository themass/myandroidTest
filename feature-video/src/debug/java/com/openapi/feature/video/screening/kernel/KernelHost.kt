package com.openapi.feature.video.screening.kernel

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.graphics.SurfaceTexture
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openapi.core.config.PlaybackEnginePrefs
import java.io.File

/**
 * 无缝换内核。
 *
 * 两套内核各占一格 [TextureView]，交接按「新内核静音准备 → 第一帧上屏 → 提层 → 旧内核退回」走，
 * 所以中途不黑屏、不从 0 重来，进度、倍速、字幕都不动。
 *
 * 内核选择和设置页共用 [PlaybackEnginePrefs]，在这里换完，别的页面打开视频也是同一套。
 */
internal class KernelHost(private val context: Context) {

    val view = FrameLayout(context).apply { setBackgroundColor(0xFF000000.toInt()) }

    var kernel by mutableStateOf(Kernel.ofPref(PlaybackEnginePrefs.getEngine(context)))
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    /** 用户想要的状态。内核还没起播时 [playing] 是 false，但交接要以这个为准 */
    private var wantPlaying = false
    /** 正在后台准备新内核，控件可以置灰并显示「换内核…」 */
    var switching by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var ratio by mutableFloatStateOf(0f)
        private set

    private var source: Uri? = null
    private var sourceFile: File? = null
    private var speed = 1f
    private var fill = false
    private var active: Slot? = null
    private var pending: Slot? = null
    /** 刚交接完的旧内核：暂停静音留着，新内核在这段时间里出错还能退回去 */
    private var retired: Slot? = null
    private var switchStartedAt = 0L
    private var retireAt = 0L
    /** 打开失败时只自动兜一次，避免两个内核互相踢皮球 */
    private var fallbackTried = false
    private inner class Slot(val kind: Kernel, val surfaceView: TextureView, val player: KernelPlayer) {
        /** TextureView 给出的 Surface，释放时要跟着收掉 */
        var surface: Surface? = null
        var attached = false
        var opened = false
        var openAt = 0L
        var ready = false
        var failed = false
    }

    private val callbacks = object : KernelCallbacks {
        override fun onReady(player: KernelPlayer) {
            val slot = slotOf(player) ?: return
            if (!slot.opened) return
            slot.ready = true
            player.setSpeed(speed)
            player.seekTo(if (slot === pending) position else slot.openAt)
            if (wantPlaying) player.play() else player.pause()
        }

        override fun onFirstFrame(player: KernelPlayer) {
            val slot = pending?.takeIf { it.player === player } ?: run {
                // 原内核第一帧：没有在换内核，直接同步一次画面比例
                syncFromActive()
                return
            }
            commit(slot)
        }

        override fun onEnded(player: KernelPlayer) {
            val slot = slotOf(player) ?: return
            // 交接途中对面直接放到尾：没有第一帧可等，直接交接
            if (slot === pending) {
                commit(slot)
                return
            }
            // 只是这一遍放完了，用户要看的意图不变：拖回前面应该接着播
            if (player === active?.player) playing = false
        }

        override fun onFailure(player: KernelPlayer, reason: String) {
            val slot = slotOf(player) ?: return
            // 同一个内核只处理第一次失败：后面还会补一枪，别把已经接上的画面再打回黑屏
            if (slot.failed) return
            slot.failed = true
            when {
                player === pending?.player -> {
                    failure = "换到 ${pending?.kind?.title} 失败，仍在 ${kernel.title} 上播放"
                    abortPending()
                }
                // 刚交接过去的内核出错：旧内核还在宽限期里，直接退回去
                player === active?.player && retired != null -> {
                    val back = retired
                    failure = "${active?.kind?.title} 播不了这个片源，已退回 ${back?.kind?.title}"
                    active = back
                    retired = null
                    back?.let { restore(it) }
                }
                // 打开就失败、又没有旧内核可退：换个内核顶上去，免得进来就是黑屏
                player === active?.player && !fallbackTried && source != null -> {
                    fallbackTried = true
                    failure = "${active?.kind?.title} 播不了这个片源，已换成 ${fallbackOf(active?.kind ?: kernel).title}"
                    handoff(fallbackOf(active?.kind ?: kernel), persist = false)
                }
                player === active?.player -> {
                    failure = reason
                    playing = false
                }
            }
        }
    }

    fun open(uri: Uri, file: File?, startMs: Long) {
        source = uri
        sourceFile = file
        failure = null
        duration = 0
        ratio = 0f
        speed = 1f
        position = startMs
        playing = true
        wantPlaying = true
        fallbackTried = false
        releaseAll()
        active = spawn(kernel).also { start(it, startMs) }
    }

    fun play() {
        wantPlaying = true
        playing = true
        active?.player?.play()
    }

    fun pause() {
        wantPlaying = false
        playing = false
        active?.player?.pause()
    }

    fun toggle() {
        if (wantPlaying) pause() else play()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0)
        position = target
        active?.player?.seekTo(target)
    }

    fun setSpeed(value: Float) {
        speed = value
        active?.player?.setSpeed(value)
    }

    /** 填满画面：放大画面槽并裁边，等价于 Exo 的 RESIZE_MODE_ZOOM。 */
    fun setFill(value: Boolean) {
        fill = value
        applyScale()
    }

    fun select(kind: Kernel) {
        if (kind == kernel || pending != null) return
        if (source == null) {
            PlaybackEnginePrefs.setEngine(context, kind.pref)
            kernel = kind
            return
        }
        handoff(kind, persist = true)
    }

    /**
     * 起一个新内核把当前画面接过去。
     * [persist] 为真时写回设置（用户主动选的）；自动兜底不写，用户的偏好保持原样。
     */
    private fun handoff(kind: Kernel, persist: Boolean) {
        if (persist) PlaybackEnginePrefs.setEngine(context, kind.pref)
        switching = true
        switchStartedAt = SystemClock.elapsedRealtime()
        val next = spawn(kind)
        pending = next
        next.player.setVolume(0f)
        start(next, position)
        syncFromActive()
        if (wantPlaying) next.player.play()
    }

    private fun fallbackOf(kind: Kernel): Kernel = when (kind) {
        Kernel.EXO -> Kernel.IJK
        Kernel.IJK, Kernel.SYSTEM -> Kernel.EXO
    }

    /** 每 125ms 由 UI 调用：同步状态，并给换内核兜一个超时。 */
    fun tick() {
        pending?.let { slot ->
            val waited = SystemClock.elapsedRealtime() - switchStartedAt
            when {
                // 已经到片尾：seek 过去不会再出帧，准备好就算交接完
                slot.ready && slot.player.position >= slot.player.duration - 400 -> commit(slot)
                waited > SWITCH_TIMEOUT_MS && (slot.ready || slot.player.ratio > 0f) ->
                    // 准备过但迟迟没有画面：有些源第一帧来得晚，先顶上总比一直吊着好
                    commit(slot)
                waited > SWITCH_TIMEOUT_MS -> {
                    failure = "${slot.kind.title} 没能放出画面，仍在 ${kernel.title} 上播放"
                    abortPending()
                }
            }
        }
        retired?.let { slot ->
            if (SystemClock.elapsedRealtime() > retireAt) {
                drop(slot)
                retired = null
            }
        }
        syncFromActive()
    }

    fun release() = releaseAll()

    private fun start(slot: Slot, at: Long) {
        val uri = source ?: return
        slot.openAt = at
        if (!slot.attached) return
        slot.opened = true
        slot.player.setSpeed(speed)
        slot.player.open(uri, sourceFile, at)
    }

    private fun slotOf(player: KernelPlayer): Slot? = when {
        active?.player === player -> active
        pending?.player === player -> pending
        retired?.player === player -> retired
        else -> null
    }

    private fun commit(slot: Slot) {
        if (pending !== slot) return
        pending = null
        switching = false
        retired?.let { leftover ->
            drop(leftover)
            retired = null
        }
        val old = active
        active = slot
        kernel = slot.kind
        slot.player.setVolume(1f)
        // 交接靠层级而不是透明度：透明图层在部分机型上根本不建 surface
        view.bringChildToFront(slot.surfaceView)
        if (!wantPlaying) slot.player.pause()
        applyScale()
        old?.let { previous ->
            // 不立刻释放：静音压在下面留一小会儿，新内核若在这期间报错可以直接退回去
            previous.player.setVolume(0f)
            previous.player.pause()
            retired = previous
            retireAt = SystemClock.elapsedRealtime() + RETIRE_GRACE_MS
        }
    }

    /** 从宽限期的旧内核退回来：它一直在原位置暂停着，直接接着播。 */
    private fun restore(slot: Slot) {
        kernel = slot.kind
        PlaybackEnginePrefs.setEngine(context, slot.kind.pref)
        slot.player.setSpeed(speed)
        slot.player.seekTo(position)
        slot.player.setVolume(1f)
        view.bringChildToFront(slot.surfaceView)
        if (wantPlaying) slot.player.play() else slot.player.pause()
        applyScale()
    }

    private fun abortPending() {
        val slot = pending ?: return
        pending = null
        switching = false
        drop(slot)
    }

    private fun spawn(kind: Kernel): Slot {
        // 用 TextureView 而不是 SurfaceView：SurfaceView 被上层同尺寸的兄弟挡住时，这台机器上
        // 根本不创建 surface（只有旧内核那格在渲染），换内核就永远等不到第一帧。
        // TextureView 参与普通视图绘制，没出帧之前是透明的，旧画面自然透出来，正好做无缝交接。
        val textureView = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER,
            )
        }
        val player = createKernel(kind, context, callbacks)
        val slot = Slot(kind, textureView, player)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                slot.surface?.release()
                val surface = Surface(texture).also { slot.surface = it }
                player.attach(surface)
                slot.attached = true
                if (!slot.opened) start(slot, if (slot === pending) position else slot.openAt)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                slot.surface?.release()
                slot.surface = null
                slot.attached = false
                return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }
        // 放到最上面：它没出帧时是透明的，旧内核的画面还在下面透出来
        view.addView(textureView)
        return slot
    }

    private fun drop(slot: Slot) {
        view.removeView(slot.surfaceView)
        slot.player.release()
        slot.surface?.release()
        slot.surface = null
    }

    private fun syncFromActive() {
        val player = active?.player ?: return
        if (player.playing) position = player.position
        if (player.duration > 0) duration = player.duration
        if (player.ratio > 0f && player.ratio != ratio) {
            ratio = player.ratio
            if (fill) applyScale()
        }
        // 只在内核真的起播/停下时同步；准备中内核一定是 false，别把用户的播放意图改掉
        if (player.ready) playing = player.playing
    }

    private fun applyScale() {
        val width = view.width
        val height = view.height
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            val params = child.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (fill && ratio > 0f && width > 0 && height > 0) {
                val box = width.toFloat() / height
                val scale = if (ratio > box) ratio / box else box / ratio
                params.width = (width * scale).toInt().coerceAtLeast(1)
                params.height = (height * scale).toInt().coerceAtLeast(1)
            } else {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.height = FrameLayout.LayoutParams.MATCH_PARENT
            }
            child.layoutParams = params
        }
    }

    private fun releaseAll() {
        pending?.let { drop(it) }
        retired?.let { drop(it) }
        active?.let { drop(it) }
        pending = null
        retired = null
        active = null
        switching = false
    }

    init {
        view.addOnLayoutChangeListener { _, _, _, _, _, oldLeft, oldTop, oldRight, oldBottom ->
            if (fill && (oldRight - oldLeft) != view.width || (oldBottom - oldTop) != view.height) applyScale()
        }
    }

    private companion object {
        /** IJK 冷启动要 load .so + seek，2 秒打不住 */
        const val SWITCH_TIMEOUT_MS = 5000L

        /** 交接后旧内核保留多久；这期间新内核报错可以直接退回 */
        const val RETIRE_GRACE_MS = 5000L
    }
}
