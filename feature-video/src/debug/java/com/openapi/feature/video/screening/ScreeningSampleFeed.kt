package com.openapi.feature.video.screening

import com.openapi.core.config.Constants

/**
 * 放映场的频道分类 + 取不到真实数据时的随包样例回落。
 *
 * 样例也做成 [RemoteEntry]，页面只有一套渲染路径：真实数据和样例的差别只剩
 * [ScreeningFeedStore.Page.fromSample] 这一个标记，不用到处写 if/else。
 */
internal enum class FeedCategory(
    val label: String,
    /** 接口路径（还没拼 base url）。 */
    val api: String,
    /** 取不到真实数据时的样例。 */
    val fallback: List<RemoteEntry>,
) {
    ALL("全部", Constants.API_RECOMMEND_URL, sampleChannels(null)),
    MOVIE("电影", Constants.API_RECOMMEND_MOVIE_URL, sampleChannels("电影")),
    NIGHT("夜色", Constants.API_RECOMMEND_NIGHT_URL, sampleChannels("夜色")),
    CUSTOMER("自定义", Constants.API_RECOMMEND_CUSTOME_URL, sampleChannels("自定义")),
}

/** 频道样例卡；`category` 为 null 表示「全部」。 */
internal fun sampleChannels(category: String?): List<RemoteEntry> =
    channels.filter { category == null || it.category == category }.map { channel ->
        RemoteEntry(
            key = "sample:channel:${channel.cover}",
            title = channel.title,
            description = "${channel.reels.size} 个片段",
            imageUrl = null,
            ratio = channel.ratio,
            actionUrl = "$SAMPLE_CHANNEL_SCHEME${channel.cover}",
            needsLazyUrl = false,
            channelParam = null,
            isImageCard = false,
        )
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

/** 样例图集列表。 */
internal fun sampleAlbums(): List<RemoteEntry> = albumsByCover().values.map { album ->
    RemoteEntry(
        key = "$SAMPLE_ALBUM_SCHEME${album.id}",
        title = album.title,
        description = album.description,
        imageUrl = null,
        ratio = album.coverRatio,
        actionUrl = "$SAMPLE_ALBUM_SCHEME${album.id}",
        needsLazyUrl = false,
        channelParam = null,
        isImageCard = true,
    )
}

/** 样例图集内的图片。 */
internal fun sampleAlbumImages(albumId: String): List<RemoteEntry> =
    albums[albumId]?.shots.orEmpty().map { shot ->
        RemoteEntry(
            key = "sample:shot:$albumId:${shot.id}",
            title = shot.caption,
            description = shot.caption,
            imageUrl = null,
            ratio = shot.ratio,
            actionUrl = "asset://${shot.asset}",
            needsLazyUrl = false,
            channelParam = null,
            isImageCard = true,
        )
    }

/** 样例频道卡 → 原来的 [Channel]（拿它的片单）。 */
internal fun sampleChannelFor(entry: RemoteEntry): Channel? =
    entry.actionUrl.takeIf { it.startsWith(SAMPLE_CHANNEL_SCHEME) }
        ?.removePrefix(SAMPLE_CHANNEL_SCHEME)?.let { cover -> channels.firstOrNull { it.cover == cover } }

/** 样例片单条目 → 随包 asset 名；不是样例外返回 null。 */
internal fun RemoteEntry.assetId(): String? = when {
    key.startsWith(SAMPLE_REEL_SCHEME) -> key.removePrefix(SAMPLE_REEL_SCHEME)
    actionUrl.startsWith("asset://") -> actionUrl.removePrefix("asset://").removeSuffix(".mp4")
    else -> null
}

/** 样例图集条目 → [Album]；不是样例外返回 null。 */
internal fun sampleAlbumFor(entry: RemoteEntry): Album? =
    entry.actionUrl.takeIf { it.startsWith(SAMPLE_ALBUM_SCHEME) }
        ?.removePrefix(SAMPLE_ALBUM_SCHEME)?.let { id -> albums[id] }

private fun albumsByCover(): Map<String, Album> = albums

/** 样例条目的 key/地址前缀：一眼能和真实数据区分开，页面据此走随包分支。 */
private const val SAMPLE_CHANNEL_SCHEME = "sample://channel/"
private const val SAMPLE_REEL_SCHEME = "sample:reel:"
private const val SAMPLE_ALBUM_SCHEME = "sample:album:"
