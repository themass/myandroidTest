package com.openapi.ks.myapp.content.sound

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.log.LogUtil
import com.openapi.core.config.Constants
import com.openapi.core.model.SoundItemsVo
import com.openapi.retromusic.helper.MusicPlayerRemote
import com.openapi.retromusic.model.Song
import com.openapi.retromusic.model.remoteSong
import com.openapi.retromusic.service.MusicService
import com.openapi.retromusic.util.PreferenceUtil

/**
 * 频道声音页的播放服务。播放后端是 :retromusic 的 [MusicService]，与本地音乐共用同一个播放队列：
 * 播放频道歌单会替换当前的本地队列。
 *
 * 对外的方法和广播与之前的 MediaPlayer 版本保持一致，声音页的界面代码不用改。
 * 当前曲目、进度、时长由定时器从 :retromusic 读取后，用原来的广播发给界面。
 */
class PlayService : Service() {

    companion object {
        const val UPDATE_ACTION = "com.ok123find.top.action.UPDATE_ACTION"
        const val CTL_ACTION = "com.ok123find.top.action.CTL_ACTION"
        /** 播放模式变更（单曲 / 列表循环 / 顺序 / 随机），与 [EXTRA_PLAY_MODE] 搭配 */
        const val ACTION_PLAY_MODE_CHANGED = "com.ok123find.top.action.PLAY_MODE_CHANGED"
        const val EXTRA_PLAY_MODE = "playMode"
        const val MUSIC_CURRENT = "com.ok123find.top.action.MUSIC_CURRENT"
        const val MUSIC_DURATION = "com.ok123find.top.action.MUSIC_DURATION"
        const val MUSIC_PREPAREING = "com.ok123find.top.action.MUSIC_PREPAREING"
        const val MUSIC_PREPARED = "com.ok123find.top.action.MUSIC_PREPARED"
        const val CURRENTTIME = "currentTime"
        const val DURATION = "duration"
        const val CURRENT = "current"

        private const val TICK_MS = 1000L
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 3f

        private fun clampPlayMode(mode: Int): Int = if (mode < 1 || mode > 4) 3 else mode
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    /** 声音页的歌单（频道接口返回的顺序）。 */
    private var playlist: List<SoundItemsVo> = emptyList()
    /** 上一次放进 :retromusic 队列的歌曲 id，按这个判断要不要重新打开队列。 */
    private var queueIds: List<Long> = emptyList()
    private var current = -1
    /** 1 单曲循环、2 列表循环、3 顺序、4 随机。 */
    private var playMode = 3
    private var playbackSpeed = 1f

    /** 服务还没连上 :retromusic 时收到的播放请求，连上后执行。 */
    private var pendingStart: Pair<Int, Int>? = null
    private var trackId = Long.MIN_VALUE
    private var reportedDuration = -1

    private var myReceiver: BroadcastReceiver? = null
    private var playModeBridge: BroadcastReceiver? = null

    private val musicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            MusicPlayerRemote.musicService = (service as MusicService.MusicBinder).service
            applyPlayMode(playMode)
            val pending = pendingStart
            pendingStart = null
            if (pending != null) {
                startFrom(pending.first, pending.second)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            MusicPlayerRemote.musicService = null
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogUtil.i("service created")
        playMode = clampPlayMode(PreferenceUtils.getPrefInt(applicationContext, Constants.MUSIC_PLAY_MODE_PREF, 3))
        playbackSpeed = PreferenceUtil.playbackSpeed
        bindService(Intent(this, MusicService::class.java), musicConnection, Context.BIND_AUTO_CREATE)

        myReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val control = intent.getIntExtra("control", -1)
                if (control in 1..4) {
                    setPlayMode(control)
                }
            }
        }
        registerReceiver(myReceiver, IntentFilter(CTL_ACTION))

        playModeBridge = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_PLAY_MODE_CHANGED != intent.action) return
                val mode = intent.getIntExtra(EXTRA_PLAY_MODE, -1)
                if (mode in 1..4) {
                    playMode = mode
                    applyPlayMode(mode)
                }
            }
        }
        val modeFilter = IntentFilter(ACTION_PLAY_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playModeBridge, modeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playModeBridge, modeFilter)
        }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        myReceiver?.let { unregisterReceiver(it) }
        myReceiver = null
        playModeBridge?.let { unregisterReceiver(it) }
        playModeBridge = null
        try {
            unbindService(musicConnection)
        } catch (e: IllegalArgumentException) {
            LogUtil.e(e)
        }
        super.onDestroy()
    }

    // ---- 播放模式 -----------------------------------------------------------------

    fun getPlayMode(): Int = playMode

    /** 1 单曲循环、2 列表循环、3 顺序（列表结束停）、4 随机。 */
    fun setPlayMode(mode: Int) {
        playMode = clampPlayMode(mode)
        PreferenceUtils.setPrefInt(applicationContext, Constants.MUSIC_PLAY_MODE_PREF, playMode)
        applyPlayMode(playMode)
        sendBroadcast(Intent(ACTION_PLAY_MODE_CHANGED).putExtra(EXTRA_PLAY_MODE, playMode))
    }

    /** 把播放模式交给 :retromusic：重复和随机由它的播放器执行。 */
    private fun applyPlayMode(mode: Int) {
        val service = MusicPlayerRemote.musicService ?: return
        service.changeRepeatMode(when (mode) {
            1 -> MusicService.REPEAT_MODE_THIS
            2 -> MusicService.REPEAT_MODE_ALL
            else -> MusicService.REPEAT_MODE_NONE
        })
        service.setShuffleMode(if (mode == 4) MusicService.SHUFFLE_MODE_SHUFFLE else MusicService.SHUFFLE_MODE_NONE)
    }

    /** 用户按「下一首」：顺序 / 列表循环 / 随机 下标计算。 */
    fun computeNextIndex(cur: Int, count: Int): Int {
        if (count <= 0) return 0
        if (playMode == 4) return pickRandomDistinct(cur, count)
        if (cur + 1 < count) return cur + 1
        if (playMode == 2) return 0
        return count - 1
    }

    /** 用户按「上一首」。 */
    fun computePreviousIndex(cur: Int, count: Int): Int {
        if (count <= 0) return 0
        if (playMode == 4) return pickRandomDistinct(cur, count)
        if (cur - 1 >= 0) return cur - 1
        if (playMode == 2) return count - 1
        return 0
    }

    /** 随机下一首，尽量避免与当前首相同（列表大于 1 时）。 */
    private fun pickRandomDistinct(cur: Int, size: Int): Int {
        if (size <= 1) return 0
        var r: Int
        var guard = 0
        do {
            r = (Math.random() * size).toInt()
            guard++
        } while (r == cur && guard < 64)
        return r
    }

    // ---- 歌单与当前曲目 -----------------------------------------------------------

    fun setData(mp3Infos: List<SoundItemsVo>?) {
        playlist = mp3Infos ?: emptyList()
    }

    fun getPlaylist(): List<SoundItemsVo> = playlist

    fun getCurrentSongIndex(): Int = current

    fun getCurrentTrack(): SoundItemsVo? = playlist.getOrNull(current)

    // ---- 播放控制 -----------------------------------------------------------------

    /** 从指定位置开始播放（点列表项）。 */
    fun playPosi(position: Int) {
        if (position !in playlist.indices) return
        LogUtil.i("service play ：$position")
        current = position
        sendBroadcast(Intent(UPDATE_ACTION).putExtra(CURRENT, current))
        startFrom(position, 0)
    }

    /** 从当前曲目的指定进度开始播放（拖动进度条后）。 */
    fun play(positionMs: Int) {
        if (current !in playlist.indices) return
        startFrom(current, positionMs)
    }

    fun next() {
        if (playlist.isEmpty()) return
        playPosi(computeNextIndex(current, playlist.size))
    }

    fun previous() {
        if (playlist.isEmpty()) return
        playPosi(computePreviousIndex(current, playlist.size))
    }

    fun pause() {
        if (MusicPlayerRemote.isPlaying) {
            MusicPlayerRemote.pauseSong()
        }
    }

    fun resume() {
        if (!MusicPlayerRemote.isPlaying) {
            MusicPlayerRemote.resumePlaying()
        }
    }

    fun isPlaying(): Boolean = MusicPlayerRemote.isPlaying

    /** 当前进度（毫秒）。 */
    fun getPosition(): Int = MusicPlayerRemote.songProgressMillis

    fun stop() {
        pause()
    }

    /** 倍速（0.25～3）。:retromusic 的服务监听这个偏好，会自己应用到播放器上。 */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        PreferenceUtil.playbackSpeed = playbackSpeed
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    private fun startFrom(position: Int, positionMs: Int) {
        if (MusicPlayerRemote.musicService == null) {
            pendingStart = position to positionMs
            return
        }
        val songs = playlist.map { it.toSong() }
        val ids = songs.map { it.id }
        val target = songs[position]
        if (ids != queueIds) {
            queueIds = ids
            MusicPlayerRemote.openQueue(songs, position, true)
        } else {
            // 队列可能被本地音乐替换过（或被随机打乱），按歌曲 id 在队列里找位置
            val index = MusicPlayerRemote.playingQueue.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                MusicPlayerRemote.playSongAt(index)
            } else {
                queueIds = ids
                MusicPlayerRemote.openQueue(songs, position, true)
            }
        }
        if (positionMs > 0) {
            MusicPlayerRemote.seekTo(positionMs)
        }
        sendBroadcast(Intent(MUSIC_PREPAREING))
    }

    /** 把 :retromusic 的当前状态同步成界面需要的广播。 */
    private fun tick() {
        if (playlist.isEmpty() || MusicPlayerRemote.musicService == null) return
        val song = MusicPlayerRemote.currentSong
        if (song.id != trackId) {
            trackId = song.id
            reportedDuration = -1
            sendBroadcast(Intent(MUSIC_PREPAREING))
        }
        val index = queueIds.indexOf(song.id)
        if (index >= 0 && index != current) {
            current = index
            sendBroadcast(Intent(UPDATE_ACTION).putExtra(CURRENT, current))
        }
        val duration = MusicPlayerRemote.songDurationMillis
        if (duration > 0 && duration != reportedDuration) {
            reportedDuration = duration
            sendBroadcast(Intent(MUSIC_DURATION).putExtra(DURATION, duration))
            sendBroadcast(Intent(MUSIC_PREPARED))
        }
        if (MusicPlayerRemote.isPlaying) {
            sendBroadcast(Intent(MUSIC_CURRENT).putExtra(CURRENTTIME, MusicPlayerRemote.songProgressMillis))
        }
    }

    private fun SoundItemsVo.toSong(): Song = remoteSong(
        url = file.orEmpty(),
        title = name.orEmpty(),
        artist = "",
        durationMs = 0L,
    )

    inner class LocalBinder : Binder() {
        fun getService(): PlayService = this@PlayService
    }
}
