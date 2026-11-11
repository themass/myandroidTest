package com.openapi.uikit.permission

import android.Manifest
import android.os.Build

/**
 * 业务里常用的权限组合。集中在这里处理不同 Android 版本的差异，调用方不需要关心 API 等级。
 *
 * 约定：每个成员是 **一个完整请求**（[PermissionRequest]），已经带好用户能看懂的理由，
 * 直接交给 [PermissionGatewayHandle.request]。需要自定义理由时用 `Permissions.camera("你的理由")`。
 *
 * 新增权限时只改这里：不要再在页面里手写 `Manifest.permission.XXX`，也不要在页面里判断
 * `Build.VERSION.SDK_INT`。
 */
object Permissions {

    /** 拍照（相机）。 */
    @JvmField
    val CAMERA: PermissionRequest = camera()

    /** 录音（语音消息、语音识别）。 */
    @JvmField
    val RECORD_AUDIO: PermissionRequest = recordAudio()

    /** 定位（附近的内容、附近的人）。 */
    @JvmField
    val LOCATION: PermissionRequest = location()

    /** 发通知（下载进度、推送）。Android 13 以下不需要，返回空列表。 */
    @JvmField
    val NOTIFICATIONS: PermissionRequest = notifications()

    fun camera(rationale: String = "拍照需要相机权限") = PermissionRequest(
        arrayOf(Manifest.permission.CAMERA),
        rationale,
    )

    fun recordAudio(rationale: String = "发送语音消息需要使用麦克风") = PermissionRequest(
        arrayOf(Manifest.permission.RECORD_AUDIO),
        rationale,
    )

    fun location(rationale: String = "为你推荐附近的内容需要定位权限") = PermissionRequest(
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        rationale,
    )

    /** Android 13 起通知要运行时申请；更早的系统装完就已经授权，不需要弹框。 */
    fun notifications(rationale: String = "开启通知才能收到下载进度和消息提醒"): PermissionRequest =
        PermissionRequest(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            },
            rationale,
        )

    /**
     * 读取相册图片。
     *
     * 只是「选一张图」的场景不要用这个：优先用系统 Photo Picker（`PickVisualMedia`，无需任何权限）。
     * 只有需要扫描整个相册（本地相册页、图片选择器自己画列表）时才申请。
     */
    @JvmStatic
    fun readImages(rationale: String = "浏览手机相册需要读取图片权限"): PermissionRequest = PermissionRequest(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        rationale,
    )

    /** 扫描本地音乐库；同样是 Android 13 起要换成细分媒体权限。 */
    @JvmStatic
    fun readAudio(rationale: String = "扫描本地音乐需要读取音频权限"): PermissionRequest = PermissionRequest(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        rationale,
    )

    /** 扫描本地视频库。 */
    @JvmStatic
    fun readVideo(rationale: String = "扫描本地视频需要读取视频权限"): PermissionRequest = PermissionRequest(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        rationale,
    )
}
