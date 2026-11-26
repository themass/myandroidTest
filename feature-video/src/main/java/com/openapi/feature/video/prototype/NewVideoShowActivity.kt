package com.openapi.feature.video.prototype

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.openapi.cast.DlnaDevice
import com.openapi.cast.DlnaDiscoveryCallback
import com.openapi.cast.JavaCaster
import com.openapi.common.ui.dialog.AppDialogHost
import com.openapi.common.util.log.LogUtil
import com.openapi.core.config.Constants
import com.openapi.core.favorite.FavoriteUtil
import com.openapi.core.db.FavoriteVo
import com.openapi.core.model.RecommendVo
import com.openapi.feature.video.R
import com.openapi.feature.video.data.VideoUtil
import com.openapi.feature.video.subtitle.local.LocalSubtitleStore
import com.openapi.feature.video.subtitle.realtime.RealtimeSubtitleBindings
import com.openapi.speech.subtitle.realtime.VideoRealtimeSubtitleSession
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 新播放页（实验实现），与旧的 [com.openapi.feature.video.page.VideoShowActivityLazyUrl] 完全独立，
 * 旧页面未做任何改动，随时可以退回去。
 *
 * 和旧页面的三点不同：
 * 1. 播放器只渲染画面（[BareVideoPlayer]），控件层是自绘 Compose，不渲染 GSY 的标题条；
 * 2. 全屏是同一 Activity 内的横竖屏重排，不再让 GSY 克隆播放器，
 *    所以退出全屏后标题、inset、字幕都不会错位或丢失；
 * 3. 字幕面板按设计稿分成四段，本地文件经系统文档选择器导入，支持正负延时。
 */
class NewVideoShowActivity : AppCompatActivity(), MyFavoriteViewCallbacks {

    private lateinit var player: BareVideoPlayer
    private lateinit var controlsHost: ComposeView
    private val controlsState = mutableStateOf<PlayerControlsState?>(null)
    private val subtitles = LocalSubtitleStore()
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var pendingImport: Future<*>? = null
    private var importGeneration = 0

    private var vo: RecommendVo? = null
    private var url: String? = null
    private var playbackSavedState: Bundle? = null

    private var dlnaDiscovery: DlnaDiscoveryCallback? = null
    private var dlnaCaster: JavaCaster? = null

    private var speed = 1f
    private var controlsVisible = true
    private var locked = false
    private var danmakuOn = false
    /** setupPlayer() 算一次存下来：本地文件没有弹幕源，控件与 handler 都按它走。 */
    private var local = false
    private var running = false
    private var lastSubtitle = ""

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            publishState()
            ui.postDelayed(this, TICK_MS)
        }
    }

    private val subtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(::importSubtitle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackSavedState = savedInstanceState
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_new_video)
        player = findViewById(R.id.new_player)
        controlsHost = findViewById(R.id.new_player_controls)
        readIntent()
        setupPlayer()
        setupControls()
        setupCast()
        installBackCallback()
        applyOrientation(resources.configuration)
        player.startPlayLogic()
        schedulePauseIfWasPaused(playbackSavedState)
        playbackSavedState = null
    }

    private fun readIntent() {
        vo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(Constants.CONFIG_PARAM, RecommendVo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(Constants.CONFIG_PARAM) as? RecommendVo
        } ?: RecommendVo()

        // 调试直达：`am start -n …/.NewVideoShowActivity --es np_url <地址> --es np_title <标题>`
        // 只用于回归验证播放、字幕、全屏，不影响正常入口
        val debugUrl = intent.getStringExtra(EXTRA_DEBUG_URL)
        if (!debugUrl.isNullOrEmpty()) {
            vo?.actionUrl = debugUrl
            intent.getStringExtra(EXTRA_DEBUG_TITLE)?.let { vo?.title = it }
        }
        url = vo?.actionUrl
    }

    private fun setupPlayer() {
        val local = VideoUtil.isLocalPlayUrl(url)
        this.local = local
        // content:// 不能走 ProxyCache，也不要带 Referer / UA
        val cache = !local
        val headers = if (local) null else VideoUtil.headersForRecommend(vo)
        player.setUp(url, cache, null, headers, vo?.title)
        if (local) autoLoadSidecarSubtitle()
        prepareSeekRestore(playbackSavedState)
        player.setSpeed(speed, true)
        findViewById<android.view.View>(R.id.new_player_click_target)
            .setOnClickListener { toggleControls() }
    }

    private fun setupControls() {
        publishState()
        controlsHost.setContent { PlayerControlsHost(controlsState.value, actions) }
    }

    private val actions = PlayerControlsActions(
        onBack = { handleBack() },
        onTogglePlay = {
            if (player.currentState == GSYVideoView.CURRENT_STATE_PLAYING) {
                player.onVideoPause()
            } else {
                player.onVideoResume()
            }
            controlsVisible = true
            publishState()
        },
        onSeek = { target -> player.seekTo(target) },
        onSeekCommit = { target -> player.seekTo(target) },
        onRewind = { seekBy(-REWIND_STEP_MS) },
        onForward = { seekBy(REWIND_STEP_MS) },
        onToggleFullscreen = {
            requestedOrientation = if (isLandscape()) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        },
        onToggleControls = { toggleControls() },
        onLockToggle = {
            locked = !locked
            if (locked) controlsVisible = false
            publishState()
        },
        onSubtitles = { showSubtitlePanel() },
        // 单击倍速 chip = 循环切档（文案即当前态），长按开面板精确选
        onSpeed = { cycleSpeed() },
        onSpeedPanel = { showSpeedPanel() },
        onDanmaku = {
            // 本地文件没有弹幕源：chip 已经按 local 隐藏，这里再 no-op 兜底
            if (ControlsVisibility.danmakuToggleApplies(local = local, currentlyOn = danmakuOn)) {
                danmakuOn = !danmakuOn
                Toast.makeText(
                    this,
                    if (danmakuOn) R.string.np_danmaku_on else R.string.np_danmaku_off,
                    Toast.LENGTH_SHORT,
                ).show()
                publishState()
            }
        },
        onMore = { Toast.makeText(this, R.string.np_more_local, Toast.LENGTH_SHORT).show() },
        onFavorite = { toggleFavorite() },
        onCast = { showDlnaDevices() },
    )

    private fun createState(): PlayerControlsState {
        val state = player.currentState
        val playing = state == GSYVideoView.CURRENT_STATE_PLAYING ||
            state == GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START
        return PlayerControlsState(
            title = vo?.title ?: "",
            positionMs = player.currentPositionWhenPlaying,
            durationMs = player.duration,
            playing = playing,
            buffering = state == GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START ||
                state == GSYVideoView.CURRENT_STATE_PREPAREING,
            controlsVisible = controlsVisible,
            landscape = isLandscape(),
            locked = locked,
            fullscreen = isLandscape(),
            subtitleText = lastSubtitle,
            subtitleEnabled = subtitles.enabled && subtitles.hasTrack,
            subtitleDelayMs = subtitles.delayMs,
            speed = speed,
            danmakuOn = danmakuOn,
            speedLabel = speedLabel(),
            local = local,
        )
    }

    private fun publishState() {
        val position = player.currentPositionWhenPlaying
        lastSubtitle = subtitles.textAt(position)
        controlsState.value = createState()
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPositionWhenPlaying + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        controlsVisible = true
        publishState()
    }

    private fun speedLabel(): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "$speed×"

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun toggleControls() {
        if (locked) return
        controlsVisible = !controlsVisible
        publishState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig)
        publishState()
    }

    private fun applyOrientation(configuration: Configuration) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------------------------------------------------------------- 字幕

    private fun importSubtitle(uri: Uri) {
        pendingImport?.cancel(true)
        val generation = ++importGeneration
        val name = displayName(uri)
        pendingImport = io.submit {
            try {
                getContentResolver().openInputStream(uri).use { input ->
                    if (input == null) error("No input stream")
                    subtitles.load(
                        InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()),
                        name,
                    )
                }
                ui.post {
                    if (generation != importGeneration || isFinishing) return@post
                    lastSubtitle = ""
                    Toast.makeText(
                        this,
                        getString(R.string.np_subtitle_loaded, name ?: "SRT"),
                        Toast.LENGTH_SHORT,
                    ).show()
                    publishState()
                }
            } catch (failure: Exception) {
                LogUtil.e(failure)
                ui.post {
                    if (generation != importGeneration || isFinishing) return@post
                    Toast.makeText(this, R.string.np_subtitle_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 本地视频优先用同目录同名的字幕（`Film.mp4` → `Film.zh.srt`），找不到再让用户手动挑。
     * 分区存储读不到目录时静默跳过，不打扰播放。
     */
    private fun autoLoadSidecarSubtitle() {
        val file = SidecarSubtitles.find(contentResolver, url) ?: return
        io.submit {
            val ok = runCatching {
                file.reader(Charsets.UTF_8).use { subtitles.load(it, file.name) }
            }.isSuccess
            if (!ok) return@submit
            ui.post {
                if (isFinishing) return@post
                publishState()
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    private fun showSubtitlePanel() {
        PlayerPanels.showSubtitles(this, object : PlayerPanels.SubtitleHost {
            override fun enabled(): Boolean = subtitles.enabled
            override fun hasTrack(): Boolean = subtitles.hasTrack
            override fun trackName(): String? = subtitles.displayName
            override fun delayMs(): Int = subtitles.delayMs

            override fun setEnabled(enabled: Boolean) {
                subtitles.enabled = enabled
                if (!enabled) lastSubtitle = ""
                publishState()
            }

            override fun shiftDelay(deltaMs: Int) {
                subtitles.shiftDelay(deltaMs)
                publishState()
            }

            override fun resetDelay() {
                subtitles.delayMs = 0
                publishState()
            }

            override fun importFromDevice() {
                // 有些机型文档提供方按 MIME 过滤扩展名，多给几种更稳
                // 只给文本类型：多带 */* 时华为文件选择器会落到「图片」分区，SRT 根本找不着
                subtitlePicker.launch(
                    arrayOf("text/*", "application/x-subrip", "application/x-subtitle-vtt"),
                )
            }

            override fun clearTrack() {
                subtitles.clear()
                lastSubtitle = ""
                publishState()
            }
        })
    }

    private fun showSpeedPanel() {
        PlayerPanels.showSpeeds(this, SpeedSteps.panelSteps, speed) { chosen ->
            applySpeed(chosen)
        }
    }

    /** 单击倍速 chip：按 SpeedSteps 的循环切到下一档，文案即当前态。 */
    private fun cycleSpeed() {
        applySpeed(SpeedSteps.nextAfter(speed))
    }

    private fun applySpeed(chosen: Float) {
        speed = chosen
        player.setSpeed(chosen, true)
        publishState()
    }

    // ---------------------------------------------------------------- 投屏 / 收藏

    /**
     * 新页面只接 DLNA：Google Cast 的会话控制器绑定的是旧播放器类型（要驱动它的
     * MyFavoriteView 与 CastButtonBinder），这里不重复接管，避免两套投屏状态并存。
     */
    private fun setupCast() {
        // 预留：Google Cast 的会话控制器绑定旧播放器类型，新页面先只做 DLNA。
    }

    private fun showDlnaDevices() {
        if (dlnaDiscovery == null) dlnaDiscovery = DlnaDiscoveryCallback()
        Toast.makeText(this, "正在搜索电视…", Toast.LENGTH_SHORT).show()
        dlnaDiscovery?.discover { devices ->
            runOnUiThread {
                if (devices.isEmpty()) {
                    Toast.makeText(this, "未发现可投屏的设备", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val names = devices.map { it.friendlyName }.toTypedArray()
                AppDialogHost.showItems(this, "投屏到", names, true) { which ->
                    castToDlna(devices[which])
                }
            }
        }
    }

    private fun castToDlna(device: DlnaDevice) {
        val caster = dlnaCaster ?: JavaCaster(this).also { dlnaCaster = it }
        val playUrl = url
        if (playUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.cast_url_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val title = vo?.title
        val done = { error: String? ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (error != null) "投屏失败：$error" else "已投屏到 ${device.friendlyName}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        if (VideoUtil.isLocalPlayUrl(playUrl)) {
            val path = resolveLocalPathForCast(playUrl)
            if (path == null) {
                Toast.makeText(this, "本地视频无法投屏：找不到可读文件", Toast.LENGTH_SHORT).show()
                return
            }
            caster.castLocal(device, path, title) { error -> done(error) }
        } else {
            caster.castRemote(device, playUrl, title) { error -> done(error) }
        }
    }

    /** content:// 尽量解析成真实路径；不行就复制到 cache，交给本地媒体服务托管。 */
    private fun resolveLocalPathForCast(playUrl: String): String? = try {
        when {
            playUrl.startsWith("/") -> File(playUrl).takeIf { it.canRead() }?.absolutePath
            playUrl.startsWith("file://") -> File(Uri.parse(playUrl).path!!)
                .takeIf { it.canRead() }?.absolutePath
            else -> {
                val uri = Uri.parse(playUrl)
                val direct = runCatching {
                    contentResolver.query(
                        uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null,
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull()
                if (direct != null && File(direct).canRead()) {
                    direct
                } else {
                    val out = File(cacheDir, "cast_local_${playUrl.hashCode()}.mp4")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    out.absolutePath
                }
            }
        }
    } catch (failure: Exception) {
        LogUtil.e(failure)
        null
    }

    private fun toggleFavorite() {
        val favorite: FavoriteVo = vo?.tofavorite(Constants.FavoriteType.VIDEO) ?: return
        FavoriteUtil.modLocalFavoritesAsync(this, favorite, null)
        Toast.makeText(this, R.string.np_favorite, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- 生命周期

    override fun onResume() {
        super.onResume()
        running = true
        runCatching { GSYVideoManager.onResume() }.onFailure(LogUtil::e)
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        running = false
        ui.removeCallbacks(tick)
        super.onPause()
        runCatching { GSYVideoManager.onPause() }.onFailure(LogUtil::e)
    }

    /**
     * targetSdk 37 + androidx.activity 1.11 起系统只走 OnBackPressedDispatcher，
     * `onBackPressed()` 的 override 会被完全绕过（predictive back）。返回逻辑抽到
     * [handleBack]，由这里常驻的回调和顶栏返回按钮共用，顺序由 [BackPolicy] 钉死。
     */
    private fun installBackCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    private fun handleBack() {
        when (BackPolicy.decide(locked = locked, fullscreen = isLandscape())) {
            BackIntent.Unlock -> {
                locked = false
                publishState()
            }
            BackIntent.ExitFullscreen ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            BackIntent.ClosePage -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePlaybackState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        running = false
        ui.removeCallbacks(tick)
        pendingImport?.cancel(true)
        io.shutdownNow()
        dlnaCaster?.close()
        dlnaDiscovery?.close()
        GSYVideoManager.releaseAllVideos()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 进度保存（只为新页面服务，不改旧 helper）

    private fun savePlaybackState(outState: Bundle) {
        runCatching {
            outState.putLong(KEY_POS_MS, player.currentPositionWhenPlaying)
            outState.putBoolean(
                KEY_WAS_PLAYING,
                player.currentState == GSYVideoView.CURRENT_STATE_PLAYING,
            )
        }
    }

    private fun prepareSeekRestore(saved: Bundle?) {
        val position = saved?.getLong(KEY_POS_MS, -1L) ?: -1L
        if (position > 500L) {
            player.seekOnStart = position
        }
    }

    private fun schedulePauseIfWasPaused(saved: Bundle?) {
        if (saved == null || saved.getBoolean(KEY_WAS_PLAYING, true)) return
        // 与起播有一帧竞态，稍后再暂停
        player.postDelayed({ runCatching { player.onVideoPause() } }, 450L)
    }

    private companion object {
        const val TICK_MS = 250L
        const val REWIND_STEP_MS = 10_000L
        const val EXTRA_DEBUG_URL = "np_url"
        const val EXTRA_DEBUG_TITLE = "np_title"
        const val KEY_POS_MS = "new_player_pos_ms"
        const val KEY_WAS_PLAYING = "new_player_was_playing"
    }
}

/** 供 [NewVideoShowActivity] 使用的最小接口，避免直接继承 500 行的 MyFavoriteView 回调。 */
interface MyFavoriteViewCallbacks
