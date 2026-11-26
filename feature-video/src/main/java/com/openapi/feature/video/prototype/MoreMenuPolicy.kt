package com.openapi.feature.video.prototype

/** 「更多」面板里的真实能力。没有真件的不进这个枚举（不留 toast 假按钮）。 */
internal enum class MoreAction {
    /** 分享：本地源走 local/shareVideo 的 content:// 方案（注入调用，不改 LocalVideoActions）。 */
    Share,

    /** 电视投屏（DLNA）：本页已有 showDlnaDevices 真实现。 */
    Cast,

    /** 解码器设置：与 player/PlayerMoreMenu 同一去处（Routes.setting）。 */
    DecoderSettings,

    /** 画中画：manifest 已声明 supportsPictureInPicture。 */
    Pip,
}

/**
 * 「更多」面板的能力清单判定层。
 *
 * player/PlayerMoreMenu 是 player 包内 package-private，prototype 调不到，
 * 这里按它的能力清单（倍速已由底部 chip 直出、投屏、解码器设置）在 prototype 内
 * 重建一份，再加上本页真实持有的分享与画中画。注意：
 * - 「清晰度」不在菜单里——仓库里不存在清晰度档位（EngineFallback 换的是解码内核，
 *   且 package-private，公开面是 PlaybackEngineHelper，属于设置页能力）；
 * - Google Cast 不接——它的会话控制器绑定旧播放器类型，本页只做 DLNA。
 */
internal object MoreMenuPolicy {

    val entries: List<MoreAction> = MoreAction.entries.toList()

    fun actions(local: Boolean): List<MoreAction> =
        entries.filter { it != MoreAction.Share || local }
}
