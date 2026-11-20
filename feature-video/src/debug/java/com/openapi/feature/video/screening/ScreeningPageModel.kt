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
 * WEB 和 CONFIG 都是「交回原路由 ConfigActionJump」，只是语义分开：
 * WEB = http(s) 链接进 WebView，CONFIG = `video://config?channel=` 这类配置频道
 * 进旧频道页（hsex_all 那批夜色卡；items.json 查不到这种 channelType，别拿它发请求）。
 */
internal enum class FeedRoute { CHANNEL, ALBUM, PLAY, WEB, CONFIG }

/**
 * 这张卡是不是「点进去还有一页」的东西：图集卡，或者能定出频道 key 的卡。
 * 外链卡（http/https）和配置频道卡（video://config）都算入口——原逻辑把它们
 * 路由到 WebView / 旧频道页，不是在放映场里直接播，也不进放映场内页。
 * 剩下的（直链 video://）才当可以直接播。
 */
internal val RemoteEntry.isGroupEntry: Boolean
    get() = !videoChannelKey().isNullOrEmpty() || isWebAction(actionUrl) || isConfigAction(actionUrl) ||
        sampleCoverAsset() != null || key.startsWith(SAMPLE_CHANNEL_KEY_PREFIX)

/**
 * 一个推荐位在页面上怎么响应：图集/真频道入口进内页，外链和配置频道走原路由，其余直接播。
 * 外链和 CONFIG 必须排在频道/播放之前——原逻辑（`ConfigActionJump`）对它们没有别的去处。
 */
internal fun routeOf(entry: RemoteEntry): FeedRoute = when {
    entry.isImageCard -> FeedRoute.ALBUM
    isWebAction(entry.actionUrl) -> FeedRoute.WEB
    isConfigAction(entry.actionUrl) -> FeedRoute.CONFIG
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
