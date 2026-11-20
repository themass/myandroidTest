package com.openapi.feature.video.screening

import java.net.URLDecoder

/**
 * 服务端推荐位 → 放映场数据的映射规则。
 *
 * 这些全是纯函数：不碰 Android、不碰 `RecommendVo`（那个类连着 `:core` 的 DB 模型，
 * JVM 单测里加载不了），入参一律是拆出来的普通字段，所以能直接跑 JVM 单测。
 */

/** 一个服务端推荐位（频道卡 / 视频 / 图片通用形状）。 */
internal data class RemoteEntry(
    /** 稳定 id：优先服务端 id，没有就用 actionUrl，用来存进度、分弹幕窗口。 */
    val key: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val ratio: Float,
    val actionUrl: String,
    /** 播放地址要先问一次 `/video/item/url.json` 才知道。 */
    val needsLazyUrl: Boolean,
    /** `channel=` 参数，从 actionUrl 里取；视频内页靠它继续取数。 */
    val channelParam: String?,
    /**
     * 服务端下发的 `param` 字段。图集内页要拿它当 `channel=`（旧页面
     * `ImgChannelImgListFragment.getUrl` 用的就是 `vo.param`，不是 actionUrl 里那个），
     * 所以两者要分开存，不能混成一个。
     */
    val param: String? = null,
    val isImageCard: Boolean,
)

/**
 * 稳定 id 的前缀：一眼看出这条是服务端给的、还是我们退化成地址/序号的。
 * [com.openapi.feature.video.screening.ScreeningFeedStore.playUrl] 靠 `srv:` 取服务端 id。
 */
internal const val SERVER_KEY_PREFIX = "srv:"
private const val URL_KEY_PREFIX = "url:"
private const val INDEX_KEY_PREFIX = "idx:"

/** 服务端 `rate` 存的是 长/宽，卡片要的是 宽/高；极端比例会把瀑布流撑坏，这里夹住。 */
internal fun feedRatio(rate: Float): Float = if (rate > 0f) (1f / rate).coerceIn(0.5f, 2.5f) else 1f

/**
 * 从 `actionUrl` 里取 `channel=` 参数。
 *
 * 服务端下发形如 `videoList1://video/list?channel=movie_1&keyword=`、`img://channel/list?channel=img_night`，
 * 旧的 [com.openapi.feature.content.config.ConfigActionJump] 也是把 query 原样拷进 `CONFIG_PARAM`，
 * 所以这里跟它取同一个值，两边看到的才是同一个频道。
 */
internal fun channelParamOf(actionUrl: String?): String? {
    if (actionUrl.isNullOrEmpty()) return null
    val query = actionUrl.substringAfter('?', "")
    if (query.isEmpty()) return null
    return query.split('&').firstNotNullOfOrNull { part ->
        val name = part.substringBefore('=').trim()
        if (name != "channel") return@firstNotNullOfOrNull null
        part.substringAfter('=', "").let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .takeIf { it.isNotBlank() }
    }
}

/** `video://`、`img://`、`videoList://`、`videoList1://` 这类自定义 scheme 的宿主。 */
internal fun schemeOf(actionUrl: String?): String? =
    actionUrl?.substringBefore("://", "")?.takeIf { it.isNotEmpty() && it != actionUrl }?.lowercase()

/**
 * actionUrl 是普通网页链接。原逻辑（`ConfigActionJump.configMap`）把 `http`/`https`
 * 一律路由到 WebView；放映场以前没有这一分支，外链卡被塞进播放器或直接发空
 * `channel=` 给 items.json（服务端查不到频道直接报错）。
 */
internal fun isWebAction(actionUrl: String?): Boolean =
    schemeOf(actionUrl) == "http" || schemeOf(actionUrl) == "https"

/**
 * 视频频道内页取数用的 key。原页面 `VideoChannelListFragment.getUrl` 传的是 `vo.param`：
 * 服务端真实频道行（如 `param='se8成人动漫'`）的 actionUrl 是外链、不带 `channel=`，
 * 真正的频道标识只在 param 里。param 没有才退回 actionUrl 解析值（`videoList1://…?channel=`）。
 */
internal fun RemoteEntry.videoChannelKey(): String? =
    param?.takeIf { it.isNotBlank() } ?: channelParam

/**
 * 稳定 id：进度、收藏、弹幕分窗都要用它。
 * 服务端 id 最优先（换地址不变），其次 actionUrl（地址就是它的身份），最后退到序号。
 */
internal fun feedKey(id: Long?, actionUrl: String?, fallbackIndex: Int): String = when {
    id != null && id > 0L -> "$SERVER_KEY_PREFIX$id"
    !actionUrl.isNullOrEmpty() -> "$URL_KEY_PREFIX$actionUrl"
    else -> "$INDEX_KEY_PREFIX$fallbackIndex"
}

/** 把一条推荐位组装成 [RemoteEntry]；`img` / `baseurl` 已由调用方拼成绝对地址。 */
internal fun remoteEntry(
    id: Long?,
    index: Int,
    title: String?,
    description: String?,
    imageUrl: String?,
    rate: Float,
    actionUrl: String?,
    needsLazyUrl: Boolean,
    dataType: Int,
    param: String? = null,
): RemoteEntry? {
    if (actionUrl.isNullOrEmpty()) return null
    return RemoteEntry(
        key = feedKey(id, actionUrl, index),
        title = title.orEmpty().ifBlank { "未命名" },
        description = description.orEmpty().trim(),
        imageUrl = imageUrl,
        ratio = feedRatio(rate),
        actionUrl = actionUrl,
        needsLazyUrl = needsLazyUrl,
        channelParam = channelParamOf(actionUrl),
        param = param?.takeIf { it.isNotBlank() },
        // 6 = RecommendVo.dataType_IMG_CHANNEL；scheme 兜底给没标 dataType 的老数据
        isImageCard = dataType == DATA_TYPE_IMG_CHANNEL || schemeOf(actionUrl) == "img",
    )
}

private const val DATA_TYPE_IMG_CHANNEL = 6
