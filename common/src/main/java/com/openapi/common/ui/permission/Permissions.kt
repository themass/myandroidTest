package com.openapi.common.ui.permission

import android.Manifest
import android.os.Build

/**
 * 业务里常用的权限组合。集中在这里处理不同 Android 版本的差异，调用方不需要关心 API 等级。
 */
object Permissions {

    @JvmField
    val RECORD_AUDIO: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    @JvmField
    val CAMERA: Array<String> = arrayOf(Manifest.permission.CAMERA)

    @JvmField
    val LOCATION: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /**
     * 读取相册图片。Android 13 起 READ_EXTERNAL_STORAGE 不再对图片生效，必须改用 READ_MEDIA_IMAGES。
     * 只是选一张图的场景应优先用系统 Photo Picker（无需任何权限），这里仅给需要扫描整个相册的场景。
     */
    @JvmStatic
    fun readImages(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** 扫描本地音乐库用；同样是 Android 13 起要换成细分媒体权限。 */
    @JvmStatic
    fun readAudio(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** 扫描本地视频库用。 */
    @JvmStatic
    fun readVideo(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
}
