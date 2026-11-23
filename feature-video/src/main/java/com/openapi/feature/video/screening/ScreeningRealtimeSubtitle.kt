package com.openapi.feature.video.screening

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.openapi.speech.subtitle.api.RealtimeSubtitleCallbacks
import com.openapi.speech.subtitle.realtime.RealtimeSubtitleEngine
import kotlin.math.abs

/**
 * 实时字幕（火山流式识别）接进放映厅的三样东西：地址判定、seek 判定、回调桥。
 * 纯判断抽成文件级函数是为了钉住行为——两边都不该依赖 Android 运行时，JVM 单测直接跑。
 */

/**
 * 实时字幕只认引擎能独立重解音频的地址：在线 http(s)、file 路径、随包样片落进缓存后的文件。
 * content://（用户在手机上挑的文件）只能走文件描述符，[RealtimeSubtitleEngine] 的接口只有 URL
 * 字符串——诚实返回 null，面板里如实说明，不装作能播。
 */
internal fun realtimeMediaUrl(uriString: String?, scheme: String?, assetCachePath: String?): String? =
    assetCachePath?.takeIf { it.isNotBlank() }
        ?: when (scheme) {
            "http", "https" -> uriString?.takeIf { it.isNotBlank() }
            // 没有 scheme 的 Uri（new Uri.Builder().path(...) 之类）toString 就是路径，直接当 file 用
            "file", null -> uriString?.removePrefix("file://")?.takeIf { it.startsWith("/") }
            else -> null
        }

/**
 * 实时字幕的 seek 判定。识别引擎是「并行重解一路静音音频喂 ASR」，它自己不会跟播放器的跳转，
 * 所以跳了就必须重启会话。判据：进度推进量与墙钟推进量（按倍速折算）之差超过阈值即认跳转。
 *
 * - 暂停期间不判：位置冻住但墙钟在走，不然每次暂停都会误重启（引擎暂停时本来就停喂，不用管）；
 * - 倍速要折算：2× 时 1 秒墙钟推进 2 秒进度，不折算就是每隔几秒误重启一次。
 */
internal fun realtimeSeekJumped(
    prevPosMs: Long,
    posMs: Long,
    prevElapsedMs: Long,
    elapsedMs: Long,
    speed: Float,
    playing: Boolean,
    thresholdMs: Long = 4000L,
): Boolean {
    if (!playing) return false
    val expectedAdvance = (elapsedMs - prevElapsedMs) * speed
    return abs((posMs - prevPosMs) - expectedAdvance) > thresholdMs
}

/**
 * [RealtimeSubtitleEngine] 到 Compose 状态的桥。
 *
 * 引擎的回调来自它的工作线程和 ASR 的 WebSocket 线程，UI 端全部在这里收拢回主线程。
 * 换代守卫是核心：[start] 先停旧引擎、再把自己这代代号 +1，旧代之后才姗姗来迟的
 * onStopped/onError 一律吞掉——引擎不认识进度条跳转，seek 后「重启新会话」和「旧会话的
 * 收尾回调」会在同一瞬间撞车，不守卫的话新会话刚连上就被旧的 onStopped 打成「已停止」。
 */
internal class RealtimeSubtitleRunner(private val context: Context) {

    /** UI 侧回调，全部在主线程触发；置 null 即断开。 */
    var onLoading: (() -> Unit)? = null
    var onText: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStopped: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var engine: RealtimeSubtitleEngine? = null
    private var generation = 0

    val running get() = engine?.isRunning == true

    /** 起新会话：先掐掉旧的，本调用之后不会再有上一代的回调进来。 */
    fun start(url: String, startPositionMs: Long, isPlaying: () -> Boolean, position: () -> Long) {
        stop()
        val mine = ++generation
        val next = RealtimeSubtitleEngine(context, url, null, object : RealtimeSubtitleEngine.PlaybackProbe {
            override fun isPlaying(): Boolean = isPlaying()
            override fun positionMs(): Long = position()
        })
        engine = next
        next.start(startPositionMs, object : RealtimeSubtitleCallbacks {
            override fun onLoading() = deliver(mine) { onLoading?.invoke() }
            override fun onPartialText(text: String) = deliver(mine) { onText?.invoke(text) }
            override fun onError(message: String) = deliver(mine) { onError?.invoke(message) }
            override fun onStopped() = deliver(mine) { onStopped?.invoke() }
        })
    }

    fun stop() {
        val previous = engine ?: return
        engine = null
        // 先断代再停：stop() 会同步或稍后触发 onClosed→onStopped，守卫必须在它之前生效
        generation++
        previous.stop()
    }

    private fun deliver(mine: Int, action: () -> Unit) {
        main.post { if (mine == generation) action() }
    }
}
