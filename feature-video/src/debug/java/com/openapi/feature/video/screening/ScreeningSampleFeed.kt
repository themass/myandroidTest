package com.openapi.feature.video.screening

import com.openapi.core.config.Constants

/**
 * 放映场的频道分类 + 「本地视频」用的随包样片清单。
 *
 * 服务端页面不从这里取数据：频道/片单/图集一律真接口，取不到就报空或报错。
 * 这里只剩两样真东西：FeedCategory（对应两个真接口）和随包样片（本地视频页播放的素材）。
 * 分类和旧频道页（LegacyChannelEntries）保持同口径：只有电影和夜色，「自定义」分类已下线。
 */
internal enum class FeedCategory(
    val label: String,
    /** 接口路径（还没拼 base url）。 */
    val api: String,
) {
    MOVIE("电影", Constants.API_RECOMMEND_MOVIE_URL),
    NIGHT("夜色", Constants.API_RECOMMEND_NIGHT_URL),
}

/** 样例片单：随包 assets 里的 12 秒样片；`channel` 为 null 时给全部。 */
internal fun sampleReels(channel: Channel?): List<RemoteEntry> =
    (channel?.reels ?: reels.keys).mapNotNull { id ->
        reels[id]?.let { reel ->
            RemoteEntry(
                key = "$SAMPLE_REEL_SCHEME${reel.id}",
                title = reel.title,
                description = reel.description,
                imageUrl = null,
                ratio = reel.ratio,
                actionUrl = "asset://${reel.id}.mp4",
                needsLazyUrl = false,
                channelParam = null,
                isImageCard = false,
            )
        }
    }

/** 样例片单条目 → 随包 asset 名；不是样例外返回 null。 */
internal fun RemoteEntry.assetId(): String? = when {
    key.startsWith(SAMPLE_REEL_SCHEME) -> key.removePrefix(SAMPLE_REEL_SCHEME)
    actionUrl.startsWith("asset://") -> actionUrl.removePrefix("asset://").removeSuffix(".mp4")
    else -> null
}

private const val SAMPLE_REEL_SCHEME = "sample:reel:"
