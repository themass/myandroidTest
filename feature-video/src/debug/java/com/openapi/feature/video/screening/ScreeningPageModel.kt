package com.openapi.feature.video.screening

/**
 * 放映场内页（某个频道的片单）要渲染的东西。
 *
 * 真实频道只有标题、封面地址和 `channel=` 参数；本地样片库还带着 [sample]，
 * 用来在取不到服务端数据时列出随包 assets。页面只认这一个形状。
 */
internal class ScreeningChannelPage(
    val title: String,
    val detail: String,
    /** 服务端封面地址；null 时用 [coverAsset]。 */
    val coverUrl: String?,
    /** 随包 assets 里的封面图名（`<name>.jpg`）；没有就纯色底。 */
    val coverAsset: String?,
    /** 真接口的分页参数；null 表示这个频道只能走样例。 */
    val channelParam: String?,
    /** 本地样片库；null 表示纯服务端频道。 */
    val sample: Channel?,
)

/**
 * 首页一张卡点下去该去哪。服务端推荐位五种都可能出现，页面按这个分流而不是猜。
 * WEB 现在只剩「拿不出频道 key 的纯网页」：外链文章页、内核不认的 .flv 这类，
 * 交回原路由 ConfigActionJump 进 WebView。CONFIG = `video://config?channel=` 这类配置频道
 * 进旧频道页（hsex_all 那批夜色卡；下一刀接内页列子频道）。
 */
internal enum class FeedRoute { CHANNEL, ALBUM, PLAY, WEB, CONFIG }

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
        (isWebAction(actionUrl) && !isPlayableMediaUrl(actionUrl)) || isConfigAction(actionUrl) ||
        sampleCoverAsset() != null || key.startsWith(SAMPLE_CHANNEL_KEY_PREFIX)

/**
 * 一个推荐位在页面上怎么响应：图集/真频道入口进内页，配置频道走原路由，其余直接播。
 * 可播媒体外链排最前——WebView 不播 HLS，塞进去就是黑屏；放映场内核（Exo/IJK）能直接播。
 * 「有频道 key 就进内页」排在其余外链之前（用户拍板 B 线：客户端全收）：
 * 外链频道卡（param 是真频道 key）以前掉回 WebView，用户从频道点进去「往里点就变旧 UI」
 * 的根因就在这。探针实测服务端 items.json 认这批 key（hsexhsex.men、badnews_all 各 30 行
 * 可播条目）；查不到行的频道（se8成人动漫）errno 也是 0 空列表，由内页诚实空态 +
 * [webFallbackUrl] 「在网页里打开」兜底，不拿旧页面填空。CONFIG 暂时保留旧路由：
 * 夜色卡拿 channel 发 items.json 是 0 行（hsex_all 只是 channelType 列），要等
 * 内页接上 channel.json 列子频道才能吸收，不能先砍。
 */
internal fun routeOf(entry: RemoteEntry): FeedRoute = when {
    entry.isImageCard -> FeedRoute.ALBUM
    isPlayableMediaUrl(entry.actionUrl) -> FeedRoute.PLAY
    isConfigAction(entry.actionUrl) -> FeedRoute.CONFIG
    !entry.videoChannelKey().isNullOrEmpty() -> FeedRoute.CHANNEL
    isWebAction(entry.actionUrl) -> FeedRoute.WEB
    entry.isGroupEntry -> FeedRoute.CHANNEL
    else -> FeedRoute.PLAY
}

/** 样例频道卡的 key 前缀（见 `ScreeningSampleFeed`）。 */
private const val SAMPLE_CHANNEL_KEY_PREFIX = "sample:channel:"

/** 样例频道卡的封面 asset 名；真实条目返回 null。 */
internal fun RemoteEntry.sampleCoverAsset(): String? =
    key.takeIf { it.startsWith(SAMPLE_CHANNEL_KEY_PREFIX) }?.removePrefix(SAMPLE_CHANNEL_KEY_PREFIX)

/**
 * 卡片右上角的时长：只有随包 12 秒样片能给得出真值。
 * 服务端推荐位不下发时长，所以真实条目返回 0，卡片就不显示时长，也不画进度条（分母未知）。
 */
internal fun RemoteEntry.sampleDurationMs(): Long = if (assetId() != null) SAMPLE_MS else 0L

/** 样例片单的 key，用来给「云层之上」预置一段已看进度，和 `ScreeningSampleFeed` 保持同一拼法。 */
internal fun sampleReelKey(reelId: String): String = "sample:reel:$reelId"
