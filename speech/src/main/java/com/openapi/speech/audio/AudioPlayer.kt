package com.openapi.speech.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前正在播放（或最后播放）的那条音频。 */
data class AudioPlayback(
    val mediaId: String? = null,
    val playing: Boolean = false,
)

/**
 * 进程内唯一的音频播放器，语音消息都走这里。
 *
 * 替换掉原先 `com.hrl.chaui.widget.MediaManager` 的静态 `MediaPlayer` 写法：
 * - 单实例 ExoPlayer，不再每次 new/reset，避免多实例抢占音频焦点
 * - 播放状态以 StateFlow 暴露，UI 订阅即可，不需要各页面各自记 isStart
 * - 同一时刻只播一条；点另一条会自然切换并回调上一条的「停止」
 *
 * 刻意不做后台播放/通知栏：语音消息是短音频，前台播放即可；
 * 后台播放需要 MediaSessionService，等 LiveKit/长音频场景再引入。
 */
object AudioPlayer {

    private const val TAG = "AudioPlayer"
    private var player: ExoPlayer? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(AudioPlayback())
    val state: StateFlow<AudioPlayback> = _state.asStateFlow()

    /** 初始化一次即可（建议在 Application.onCreate）。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun obtain(): ExoPlayer? {
        player?.let { return it }
        val ctx = appContext ?: return null
        val created = ExoPlayer.Builder(ctx).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(playing = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED ||
                        playbackState == Player.STATE_IDLE
                    ) {
                        _state.value = _state.value.copy(playing = false)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // 播放失败（文件损坏、地址不可达等）：记日志，把波纹停下来，不让界面一直显示播放中
                    android.util.Log.e(TAG, "播放失败 mediaId=${_state.value.mediaId}", error)
                    _state.value = AudioPlayback(mediaId = _state.value.mediaId, playing = false)
                }
            })
        }
        player = created
        return created
    }

    /**
     * 播放一条音频。若正在播的就是同一条，则切换为停止（对应老实现的「再点一次暂停」）。
     *
     * @param mediaId 用来标识这条音频（一般是消息 id），UI 据此高亮当前播放项
     * @param uri     本地 `file://` 路径或远端 http(s) 地址
     */
    fun toggle(mediaId: String, uri: String) {
        val exo = obtain() ?: return
        if (_state.value.mediaId == mediaId && exo.isPlaying) {
            exo.pause()
            _state.value = AudioPlayback(mediaId = mediaId, playing = false)
            return
        }
        exo.stop()
        exo.setMediaItem(MediaItem.fromUri(uri).buildUpon().setMediaId(mediaId).build())
        exo.prepare()
        exo.play()
        _state.value = AudioPlayback(mediaId = mediaId, playing = true)
    }

    /** 直接播放（不切换），用于 TTS 新回复自动播报。 */
    fun play(mediaId: String, uri: String) {
        val exo = obtain() ?: return
        exo.stop()
        exo.setMediaItem(MediaItem.fromUri(uri).buildUpon().setMediaId(mediaId).build())
        exo.prepare()
        exo.play()
        _state.value = AudioPlayback(mediaId = mediaId, playing = true)
    }

    fun stop() {
        player?.pause()
        _state.value = _state.value.copy(playing = false)
    }

    /** 页面退出时调用，释放底层资源。 */
    fun release() {
        player?.release()
        player = null
        _state.value = AudioPlayback()
    }
}
