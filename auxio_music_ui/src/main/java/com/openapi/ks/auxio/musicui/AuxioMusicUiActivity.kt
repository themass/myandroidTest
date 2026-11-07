package com.openapi.ks.auxio.musicui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openapi.core.net.NetQueue

/**
 * GPL-3.0 独立音乐 UI：列表 + 详情（Compose + Media3）。
 *
 * - **静态列表**：[start] 传入 [MusicTrack] 列表。
 * - **远程列表**：[startRemoteFromSoundChannel] 使用与宿主音乐频道相同的 `items.json` 接口（需在 Intent 中传入 [EXTRA_API_ROOT] 等）。
 */
class AuxioMusicUiActivity : ComponentActivity() {

    private var volleyTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        volleyTag = intent.getStringExtra(EXTRA_VOLLEY_TAG)

        val remoteRoot = intent.getStringExtra(EXTRA_API_ROOT)
        val remoteChannel = intent.getStringExtra(EXTRA_REMOTE_CHANNEL)
        val useRemote = !remoteRoot.isNullOrBlank() && !remoteChannel.isNullOrBlank()
        val staticTracks = readTracks(intent)
        if (!useRemote && staticTracks.isEmpty()) {
            finish()
            return
        }

        setContent {
            AuxioMusicTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (useRemote) {
                        val kw = intent.getStringExtra(EXTRA_REMOTE_KEYWORD).orEmpty()
                        val tag = volleyTag ?: DEFAULT_VOLLEY_TAG
                        AuxioMusicRemoteRoot(
                            apiRoot = remoteRoot,
                            channel = remoteChannel,
                            keyword = kw,
                            playlistTitle = title,
                            volleyTag = tag,
                            onFinish = { finish() },
                        )
                    } else {
                        val app = application
                        val vm: PlayerViewModel = viewModel(
                            factory = remember(staticTracks) {
                                PlayerViewModel.Factory(app, staticTracks)
                            },
                        )
                        AuxioMusicNavHost(
                            playlistTitle = title,
                            playerVm = vm,
                            onFinish = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        volleyTag?.let { NetQueue.cancelRequest(it) }
        super.onDestroy()
    }

    private fun readTracks(intent: Intent): List<MusicTrack> {
        val list = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(EXTRA_TRACKS, MusicTrack::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<MusicTrack>(EXTRA_TRACKS)
        }
        return list ?: emptyList()
    }

    companion object {
        const val EXTRA_TRACKS = "auxio_music_tracks"
        const val EXTRA_TITLE = "auxio_music_title"
        /** Volley 请求 tag，与 [onDestroy] 取消请求一致 */
        const val EXTRA_VOLLEY_TAG = "auxio_music_volley_tag"
        /** 与 [com.openapi.ks.myapp.constant.Constants.getUrl] 一致的前缀，例如 `http://host/vvv/api` */
        const val EXTRA_API_ROOT = "auxio_music_api_root"
        const val EXTRA_REMOTE_CHANNEL = "auxio_music_channel"
        const val EXTRA_REMOTE_KEYWORD = "auxio_music_keyword"

        private const val DEFAULT_VOLLEY_TAG = "auxio_music_ui"

        @JvmStatic
        fun start(activity: Activity, tracks: java.util.ArrayList<MusicTrack>, playlistTitle: String) {
            val i = Intent(activity, AuxioMusicUiActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_TRACKS, tracks)
                putExtra(EXTRA_TITLE, playlistTitle)
            }
            activity.startActivity(i)
        }

        /**
         * 使用宿主音乐列表接口（`/sound/items.json?...`）拉取列表；URL 规则见 [AuxioSoundListUrls]。
         */
        @JvmStatic
        fun startRemoteFromSoundChannel(
            activity: Activity,
            apiRoot: String,
            channel: String,
            keyword: String?,
            playlistTitle: String,
            volleyTag: String?,
        ) {
            val i = Intent(activity, AuxioMusicUiActivity::class.java).apply {
                putExtra(EXTRA_API_ROOT, apiRoot)
                putExtra(EXTRA_REMOTE_CHANNEL, channel)
                putExtra(EXTRA_REMOTE_KEYWORD, keyword ?: "")
                putExtra(EXTRA_TITLE, playlistTitle)
                putExtra(EXTRA_VOLLEY_TAG, volleyTag ?: DEFAULT_VOLLEY_TAG)
            }
            activity.startActivity(i)
        }
    }
}
