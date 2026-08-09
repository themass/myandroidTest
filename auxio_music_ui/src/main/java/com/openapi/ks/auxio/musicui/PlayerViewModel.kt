package com.openapi.ks.auxio.musicui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlayerViewModel(
    application: Application,
    initialTracks: List<MusicTrack>,
) : AndroidViewModel(application) {

    private val _playlist = MutableStateFlow(initialTracks.toList())
    val playlist: StateFlow<List<MusicTrack>> = _playlist.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        setMediaItems(_playlist.value.map { MediaItem.fromUri(it.mediaUrl) })
        addListener(
            object : Player.Listener {
                /**
                 * 底栏/按钮用「是否请求播放」而非 [Player.isPlaying]：缓冲时 isPlaying 仍为 false，
                 * 但应显示暂停（与系统播放器一致）。
                 */
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _isPlaying.value = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                }
            },
        )
        prepare()
    }

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playMode = MutableStateFlow(MusicPlaybackModeShared.read(application))
    val playMode: StateFlow<Int> = _playMode.asStateFlow()

    private var ticker: Job? = null

    private val modeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MusicPlaybackModeShared.ACTION_PLAY_MODE_CHANGED) {
                return
            }
            val m = intent.getIntExtra(MusicPlaybackModeShared.EXTRA_PLAY_MODE, 3).coerceIn(1, 4)
            _playMode.value = m
            applyPlaybackModeToPlayer(m)
        }
    }

    init {
        _isPlaying.value = player.playWhenReady
        applyPlaybackModeToPlayer(_playMode.value)
        val app = getApplication<Application>()
        val filter = IntentFilter(MusicPlaybackModeShared.ACTION_PLAY_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(modeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(modeReceiver, filter)
        }
        startTicker()
    }

    private fun playlistSize(): Int = _playlist.value.size

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(400)
                _positionMs.value = player.currentPosition
                val d = player.duration
                if (d > 0) {
                    _durationMs.value = d
                }
                _currentIndex.value = player.currentMediaItemIndex
                _isPlaying.value = player.playWhenReady
            }
        }
    }

    private fun applyPlaybackModeToPlayer(mode: Int) {
        when (mode) {
            1 -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
            2 -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            3 -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
            4 -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = true
            }
            else -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    fun appendToPlaylist(more: List<MusicTrack>) {
        if (more.isEmpty()) {
            return
        }
        player.addMediaItems(more.map { MediaItem.fromUri(it.mediaUrl) })
        _playlist.value = _playlist.value + more
    }

    fun replacePlaylist(tracks: List<MusicTrack>) {
        player.stop()
        player.clearMediaItems()
        _playlist.value = tracks.toList()
        player.setMediaItems(tracks.map { MediaItem.fromUri(it.mediaUrl) })
        player.prepare()
        player.playWhenReady = false
    }

    fun cyclePlayMode() {
        val next = MusicPlaybackModeShared.cycle(_playMode.value)
        MusicPlaybackModeShared.apply(getApplication(), next)
    }

    fun playPause() {
        if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun playAt(index: Int) {
        val n = playlistSize()
        if (n == 0 || index !in 0 until n) {
            return
        }
        player.seekToDefaultPosition(index)
        player.playWhenReady = true
        player.play()
    }

    fun seekToMs(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
    }

    fun skipNext() {
        val n = playlistSize()
        if (n == 0) {
            return
        }
        val idx = player.currentMediaItemIndex.coerceIn(0, n - 1)
        val mode = _playMode.value
        val next = when (mode) {
            4 -> MusicPlaybackModeShared.pickRandomDistinct(idx, n)
            3 -> if (idx < n - 1) idx + 1 else idx
            2 -> if (idx < n - 1) idx + 1 else 0
            1 -> if (idx < n - 1) idx + 1 else idx
            else -> if (idx < n - 1) idx + 1 else n - 1
        }
        if (mode == 3 && idx == n - 1) {
            player.pause()
            return
        }
        playAt(next)
    }

    fun skipPrev() {
        val n = playlistSize()
        if (n == 0) {
            return
        }
        val idx = player.currentMediaItemIndex.coerceIn(0, n - 1)
        val mode = _playMode.value
        val prev = when (mode) {
            4 -> MusicPlaybackModeShared.pickRandomDistinct(idx, n)
            3 -> if (idx > 0) idx - 1 else idx
            2 -> if (idx > 0) idx - 1 else n - 1
            1 -> if (idx > 0) idx - 1 else idx
            else -> if (idx > 0) idx - 1 else 0
        }
        playAt(prev)
    }

    fun trackAt(index: Int): MusicTrack? = _playlist.value.getOrNull(index)

    fun nextTrackIdBase(): Long = _playlist.value.size.toLong()

    override fun onCleared() {
        ticker?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(modeReceiver)
        } catch (_: Throwable) {
        }
        player.release()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val tracks: List<MusicTrack>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                return PlayerViewModel(application, tracks) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
