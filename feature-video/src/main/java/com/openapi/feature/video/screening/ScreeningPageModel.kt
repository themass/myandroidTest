package com.openapi.feature.video.screening

/**
 * 放映场内页（某个频道的片单）要渲染的东西。
 *
 * 真实频道只有标题、封面地址和 `channel=` 参数。随包样片库已下线：
 * 取不到数据就报空态（有 [webFallback] 时给回网页退路），不拿 assets 填屏。
 */
internal class ScreeningChannelPage(
    val title: String,
    val detail: String,
    /** 服务端封面地址；null 就纯色底。 */
    val coverUrl: String?,
    /** 真接口的分页参数；null 表示这一页取不出数据，诚实空态。 */
    val channelParam: String?,
    /** 这一页取哪种接口，见 [ScreeningPageKind]。 */
    val kind: ScreeningPageKind = ScreeningPageKind.VIDEOS,
    /**
     * 片单查不到时「在网页里打开」的地址（[webFallbackUrl]）；null = 没有网页退路。
     * 内页吸收外链频道卡（B 线）后的兜底：诚实说没有片单，但把人带回这条卡原来的网页，
     * 而不是掉回旧列表页。
     */
    val webFallback: String? = null,
)

/**
 * 首页一张卡点下去该去哪。服务端推荐位几种形状都可能出现，页面按这个分流而不是猜。
 * WEB 只剩「拿不出频道 key 的纯网页」：外链文章页、内核不认的 .flv 这类，
 * 交回原路由 ConfigActionJump 进 WebView。CONFIG 已删——夜色配置频道卡（`video://config?channel=`）
 * 由内页吸收：进去先列子频道（[videoChannelPageKind] 判取哪种接口），不再掉旧频道页。
 */
internal enum class FeedRoute { CHANNEL, ALBUM, TEXT, SOUND, PLAY, WEB }

/**
 * 放映场内页的取数方式。
 * - VIDEOS：直接取片单（items.json）——外链频道卡、`videoList1://?channel=` 频道卡、
 *   子频道下钻都走这条。
 * - SUBCHANNELS：先列子频道（channel.json）——夜色配置频道卡（`video://config?channel=hsex_all`）。
 *   探针实测 hsex_all 发 items.json 是 0 行（它只是 channelType 列），发 channel.json
 *   才列得出「18禁 / hsex.men」两行子频道；点其中一行再开一层 VIDEOS 内页取片单
 *   （hsexhsex.men 发 items.json 实测 30 行可播条目）。旧页面 VideoChannleActivity
 *   就是这个两层结构，现在两层都在新内页里。
 */
internal enum class ScreeningPageKind { VIDEOS, SUBCHANNELS, VIDEO_USERS, VIDEO_USER_ITEMS }

/**
 * 这一张卡进内页后取哪种接口：配置频道（`video://`、`teleplay://`、`movie://` + `channel=`）
 * 列子频道，其余取片单。取哪种接口跟 scheme 白名单走，不跟 channel 值的长相走——
 * `movie_1` 这类值和子频道 key 长得像，但 teleplay/movie 的第一层必须是 channel.json。
 */
/** 子频道行标记「用户片单」：video 端看 actionUrl，movie 端看 param（对齐 BodyFragment）。 */
internal const val VIDEO_USER_CHANNEL = "qvod_user"

internal fun isVideoUserHubEntry(entry: RemoteEntry): Boolean =
    entry.actionUrl == VIDEO_USER_CHANNEL || entry.param == VIDEO_USER_CHANNEL

/** user.json 的 channel 参数：movie 行 param 常为 qvod_user，退回 channelParam。 */
internal fun videoUserListChannelKey(entry: RemoteEntry): String =
    entry.param?.takeIf { it.isNotBlank() && it != VIDEO_USER_CHANNEL }
        ?: entry.channelParam?.takeIf { it.isNotBlank() }
        ?: VIDEO_USER_CHANNEL

internal fun videoChannelPageKind(entry: RemoteEntry): ScreeningPageKind = when {
    isConfigAction(entry.actionUrl) -> ScreeningPageKind.SUBCHANNELS
    isVideoUserHubEntry(entry) -> ScreeningPageKind.VIDEO_USERS
    else -> ScreeningPageKind.VIDEOS
}

/**
 * 这张卡是不是「点进去还有一页」的东西：图集卡，或者能定出频道 key 的卡。
 * 外链卡（http/https）和配置频道卡（video://config）都算入口——原逻辑把它们
 * 路由到 WebView / 旧频道页，不是在放映场里直接播，也不进放映场内页。
 * 例外：路径直接落在媒体文件上的外链（裸 m3u8/mp4，ddys 片单的影片卡就是这种）
 * 不是「一页」，是能直接播的源，不算入口。
 * 剩下的（直链 video://）才当可以直接播。
 */
internal val RemoteEntry.isGroupEntry: Boolean
    get() = !videoChannelKey().isNullOrEmpty() ||
        (isWebAction(actionUrl) && !isPlayableMediaUrl(actionUrl)) || isConfigAction(actionUrl)

/**
 * 一个推荐位在页面上怎么响应：图集/真频道入口进内页，其余直接播或交回原分发。
 * 可播媒体外链排最前——WebView 不播 HLS，塞进去就是黑屏；放映场内核（Exo/IJK）能直接播。
 * 「有频道 key 就进内页」排在其余外链之前（用户拍板 B 线：客户端全收）：
 * 外链频道卡（param 是真频道 key）以前掉回 WebView，用户从频道点进去「往里点就变旧 UI」
 * 的根因就在这。探针实测服务端 items.json 认这批 key（hsexhsex.men、badnews_all 各 30 行
 * 可播条目）；查不到行的频道（se8成人动漫）errno 也是 0 空列表，由内页诚实空态 +
 * [webFallbackUrl] 「在网页里打开」兜底，不拿旧页面填空。配置频道卡（夜色/teleplay/movie）
 * 也进 CHANNEL，内页按 [videoChannelPageKind] 先列子频道再下钻。
 * TEXT/SOUND：走 ScreeningText / ScreeningSound。
 * WEB：纯 http(s) 等外链。
 */
internal fun routeOf(entry: RemoteEntry): FeedRoute = when {
    entry.isImageCard -> FeedRoute.ALBUM
    isPlayableMediaUrl(entry.actionUrl) -> FeedRoute.PLAY
    isTextConfigAction(entry.actionUrl) -> FeedRoute.TEXT
    isSoundConfigAction(entry.actionUrl) -> FeedRoute.SOUND
    isLegacyOnlyAction(entry.actionUrl) -> FeedRoute.WEB
    isConfigAction(entry.actionUrl) -> FeedRoute.CHANNEL
    !entry.videoChannelKey().isNullOrEmpty() -> FeedRoute.CHANNEL
    isWebAction(entry.actionUrl) -> FeedRoute.WEB
    entry.isGroupEntry -> FeedRoute.CHANNEL
    else -> FeedRoute.PLAY
}
