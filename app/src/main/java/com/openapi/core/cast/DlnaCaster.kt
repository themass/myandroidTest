package com.openapi.core.cast

import android.content.Context
import android.util.Log
import com.openapi.core.cast.http.LocalMediaServer
import com.openapi.core.cast.soap.AvTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 投屏会话：一次只投一台设备、一个媒体。
 *
 * 两条路径：
 * - 在线视频：URL 直接给电视拉（首选路径，不占手机带宽）
 * - 本地视频：先起 [LocalMediaServer]，把本机地址给电视
 *
 * 状态通过 [state] 暴露给 UI；进度要轮询电视（UPnP 没有可靠的回调通道，
 * 各家电视对 SUBSCRIBE 事件的支持参差不齐，轮询是兼容性最好的做法）。
 */
class DlnaCaster(private val context: Context) {

    private companion object {
        const val TAG = "DlnaCaster"
    }

    private val _state = MutableStateFlow<CastState>(CastState.Idle)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var transport: AvTransport? = null
    private var server: LocalMediaServer? = null

    /** 投一个在线视频。 */
    suspend fun castRemote(device: DlnaDevice, url: String, title: String? = null) {
        setState(CastState.Connecting)
        transport = AvTransport(device)
        val ok = transport!!.setUri(url, title).isSuccess &&
            transport!!.play().isSuccess
        _state.value = if (ok) CastState.Playing(device, 0, 0) else CastState.Error("投屏失败：设备无响应")
    }

    /** 投一个本地文件；服务起不来（比如没有 Wi-Fi）时明确报错，而不是静默失败。 */
    suspend fun castLocal(device: DlnaDevice, file: File, title: String? = null) {
        setState(CastState.Connecting)
        val srv = server ?: LocalMediaServer().also { server = it }
        val started = withContext(Dispatchers.IO) { srv.start(file.parentFile ?: context.cacheDir) }
        if (!started) {
            stop()
            _state.value = CastState.Error("本地投屏需要与电视在同一 Wi-Fi")
            return
        }
        val localUrl = srv.urlFor(file)
        if (localUrl == null) {
            stop()
            _state.value = CastState.Error("获取本机地址失败")
            return
        }
        castRemote(device, localUrl, title)
    }

    suspend fun pause() = withContext(Dispatchers.IO) {
        transport?.pause()
        (_state.value as? CastState.Playing)?.let { _state.value = CastState.Paused(it.device) }
    }

    suspend fun resume() = withContext(Dispatchers.IO) {
        transport?.play()
        (_state.value as? CastState.Paused)?.let { _state.value = CastState.Playing(it.device, 0, 0) }
    }

    suspend fun seekTo(seconds: Int) = withContext(Dispatchers.IO) {
        transport?.seekTo(seconds)
    }

    /** 取消投屏并释放本地 HTTP 服务。 */
    fun stop() {
        runCatching { transport?.stop() }
        transport = null
        server?.stop()
        server = null
        _state.value = CastState.Idle
    }

    /** 拉一次进度（秒）。UI 按需定时调用，不要常驻轮询。 */
    suspend fun pollProgress(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val pair = runCatching { transport?.position() }.getOrNull() ?: return@withContext null
        val current = _state.value
        if (current is CastState.Playing) {
            _state.value = current.copy(positionSec = pair.first, durationSec = pair.second)
        }
        pair
    }

    private fun setState(state: CastState) {
        _state.value = state
        Log.i(TAG, "state=$state")
    }
}
