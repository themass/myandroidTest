package com.openapi.feature.video.screening.kernel

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File

/**
 * 换内核时新内核先静音准备、等到第一帧再起播，所以旧内核要等交接完成才停。
 * 三个实现都遵守同一约定：[open] 只准备不出声，[play] 才出声出画。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ExoKernel(context: Context, private val callbacks: KernelCallbacks) : KernelPlayer {

    private val player = ExoPlayer.Builder(context).build()
    private var starts = false
    private var aspect = 0f

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !starts) {
                    starts = true
                    callbacks.onReady(this@ExoKernel)
                }
                if (state == Player.STATE_ENDED) callbacks.onEnded(this@ExoKernel)
            }

            override fun onRenderedFirstFrame() = callbacks.onFirstFrame(this@ExoKernel)

            override fun onVideoSizeChanged(size: VideoSize) {
                val pixel = size.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
                if (size.width > 0 && size.height > 0) aspect = size.width * pixel / size.height
            }

            override fun onPlayerError(error: PlaybackException) =
                callbacks.onFailure(this@ExoKernel, error.message ?: "Exo 解码失败")
        })
    }

    override fun attach(surface: Surface) = player.setVideoSurface(surface)

    override fun open(uri: Uri, file: File?, startMs: Long) {
        player.setMediaItem(MediaItem.fromUri(file?.let(Uri::fromFile) ?: uri))
        if (startMs > 0) player.seekTo(startMs)
        player.volume = 0f
        player.prepare()
    }

    override fun play() {
        player.volume = 1f
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))

    override fun setSpeed(value: Float) {
        player.playbackParameters = PlaybackParameters(value)
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun release() = player.release()

    override val position: Long get() = player.currentPosition.coerceAtLeast(0)
    override val duration: Long get() = player.duration.coerceAtLeast(0)
    override val playing: Boolean get() = ready && player.isPlaying
    override val ready: Boolean get() = starts
    override val ratio: Float get() = aspect
}

/**
 * IJK（GSY `gsyvideoplayer-ex_so` 带的 FFmpeg）：冷门容器、RTSP 这类 Exo 会缺的东西。
 * 原生库随 `libijk*.so` 打包，默认的 `System.loadLibrary` 就能加载。
 * seek 必须是准确的，否则换内核时位置会跳到关键帧。
 */
internal class IjkKernel(context: Context, private val callbacks: KernelCallbacks) : KernelPlayer {

    private val app = context.applicationContext
    private val player = IjkMediaPlayer()
    private var aspect = 0f
    private var prepared = false

    init {
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L)
        player.setOnPreparedListener {
            prepared = true
            callbacks.onReady(this@IjkKernel)
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) callbacks.onFirstFrame(this@IjkKernel)
            false
        }
        player.setOnCompletionListener { callbacks.onEnded(this@IjkKernel) }
        player.setOnErrorListener { _, _, _ ->
            callbacks.onFailure(this@IjkKernel, "IJK 解码失败")
            true
        }
        player.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
            if (width > 0 && height > 0) {
                val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
                aspect = width * sar / height
            }
        }
    }

    override fun attach(surface: Surface) {
        runCatching { player.setSurface(surface) }
    }

    override fun open(uri: Uri, file: File?, startMs: Long) {
        try {
            if (file != null) player.setDataSource(file.path) else player.setDataSource(app, uri)
            player.prepareAsync()
        } catch (failure: Exception) {
            callbacks.onFailure(this, failure.message ?: "IJK 打不开这个片源")
        }
    }

    override fun play() {
        if (!prepared) return
        runCatching { player.start() }
    }

    override fun pause() {
        if (!prepared) return
        runCatching { player.pause() }
    }

    override fun seekTo(ms: Long) {
        if (!prepared) return
        runCatching { player.seekTo(ms.coerceAtLeast(0)) }
    }

    override fun setSpeed(value: Float) {
        if (prepared) runCatching { player.setSpeed(value) }
    }

    override fun setVolume(volume: Float) {
        runCatching { player.setVolume(volume, volume) }
    }

    override fun release() {
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    override val position: Long get() = if (prepared) player.currentPosition.coerceAtLeast(0) else 0L
    override val duration: Long get() = if (prepared) player.duration.coerceAtLeast(0) else 0L
    override val playing: Boolean get() = prepared && runCatching { player.isPlaying }.getOrDefault(false)
    override val ready: Boolean get() = prepared
    override val ratio: Float get() = aspect
}

/**
 * 系统 [MediaPlayer]：依赖最小，格式覆盖最窄。速度只在真正播放时应用，
 * 否则有的机器上 `playbackParams` 会把准备好的状态带乱。
 */
internal class SystemKernel(private val context: Context, private val callbacks: KernelCallbacks) : KernelPlayer {

    private val player = MediaPlayer()
    private var aspect = 0f
    private var prepared = false
    private var speed = 1f

    init {
        player.setOnPreparedListener {
            prepared = true
            callbacks.onReady(this@SystemKernel)
        }
        player.setOnCompletionListener { callbacks.onEnded(this@SystemKernel) }
        player.setOnErrorListener { _, _, _ ->
            callbacks.onFailure(this@SystemKernel, "系统解码器不支持这个片源")
            true
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) callbacks.onFirstFrame(this@SystemKernel)
            false
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) aspect = width.toFloat() / height
        }
    }

    override fun attach(surface: Surface) {
        runCatching { player.setSurface(surface) }
    }

    override fun open(uri: Uri, file: File?, startMs: Long) {
        try {
            if (file != null) player.setDataSource(file.path) else player.setDataSource(context, uri)
            player.prepareAsync()
        } catch (failure: Exception) {
            callbacks.onFailure(this, failure.message ?: "系统解码器打不开这个片源")
        }
    }

    override fun play() {
        if (!prepared) return
        runCatching {
            applySpeed()
            player.start()
        }
    }

    override fun pause() {
        if (!prepared) return
        runCatching { player.pause() }
    }

    override fun seekTo(ms: Long) {
        if (!prepared) return
        runCatching { player.seekTo(ms.coerceAtLeast(0).toInt()) }
    }

    override fun setSpeed(value: Float) {
        speed = value
        if (prepared && runCatching { player.isPlaying }.getOrDefault(false)) runCatching { applySpeed() }
    }

    private fun applySpeed() {
        if (speed == 1f) return
        runCatching { player.playbackParams = PlaybackParams().setSpeed(speed) }
    }

    override fun setVolume(volume: Float) {
        runCatching { player.setVolume(volume, volume) }
    }

    override fun release() {
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    override val position: Long get() = if (prepared) runCatching { player.currentPosition.toLong() }.getOrDefault(0L) else 0L
    override val duration: Long get() = if (prepared) runCatching { player.duration.toLong() }.getOrDefault(0L) else 0L
    override val playing: Boolean get() = prepared && runCatching { player.isPlaying }.getOrDefault(false)
    override val ready: Boolean get() = prepared
    override val ratio: Float get() = aspect
}
