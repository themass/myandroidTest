package com.openapi.ks.auxio.musicui

import android.content.Context
import android.content.Intent
import com.openapi.core.util.PreferenceUtils
import kotlin.random.Random

/**
 * 与宿主 `Constants.MUSIC_PLAY_MODE_PREF`、`PlayService` 使用同一 SharedPreferences 键与广播 Action，
 * 保证 GPL 界面与音乐频道播放模式一致。
 *
 * 模式值：1 单曲循环、2 列表循环、3 顺序、4 随机。
 */
object MusicPlaybackModeShared {
    /** 必须与 app 模块 `Constants.MUSIC_PLAY_MODE_PREF` 一致 */
    const val PREF_KEY: String = "MUSIC_PLAY_MODE_PREF"

    /** 必须与 [com.openapi.ks.myapp.service.PlayService.ACTION_PLAY_MODE_CHANGED] 一致 */
    const val ACTION_PLAY_MODE_CHANGED: String = "com.ok123find.top.action.PLAY_MODE_CHANGED"

    /** 必须与 [com.openapi.ks.myapp.service.PlayService.EXTRA_PLAY_MODE] 一致 */
    const val EXTRA_PLAY_MODE: String = "playMode"

    fun read(context: Context): Int =
        PreferenceUtils.getPrefInt(context.applicationContext, PREF_KEY, 3).coerceIn(1, 4)

    /** 写入偏好并发送广播（宿主 `PlayService` 会同步内部播放模式）。 */
    fun apply(context: Context, mode: Int) {
        val m = mode.coerceIn(1, 4)
        val app = context.applicationContext
        PreferenceUtils.setPrefInt(app, PREF_KEY, m)
        app.sendBroadcast(
            Intent(ACTION_PLAY_MODE_CHANGED).putExtra(EXTRA_PLAY_MODE, m),
        )
    }

    fun cycle(current: Int): Int = when (current) {
        3 -> 2
        2 -> 1
        1 -> 4
        else -> 3
    }

    /** 与宿主随机切歌逻辑一致 */
    fun pickRandomDistinct(current: Int, size: Int): Int {
        if (size <= 1) return 0
        var r: Int
        var guard = 0
        do {
            r = Random.nextInt(size)
            guard++
        } while (r == current && guard < 64)
        return r
    }
}
