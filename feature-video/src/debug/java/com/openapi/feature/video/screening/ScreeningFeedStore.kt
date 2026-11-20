package com.openapi.feature.video.screening

import android.content.Context
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.awaitGet
import com.openapi.common.net.awaitInfoList
import com.openapi.common.net.request.InfoListVo
import com.openapi.core.config.Constants
import com.openapi.core.model.RecommendUrl
import com.openapi.core.model.RecommendVo

/**
 * 放映场的数据来源：真实服务端接口，取不到时回落随包样例。
 *
 * 走的是 app 自己的网络层（`BaseService` + `awaitInfoList`），不自己拼 OkHttp：
 * 服务端返回体是 AES 加密的、密钥来自请求自己的 User-Agent（见 `BaseRequest.authkey`），
 * 绕开 `BaseRequest`/`GsonRequest` 根本解不出来。接口、参数、模型都跟旧频道页同一套
 * （`LegacyChannelEntries` / `GSYAutoVideoListFragment`），所以这里取到的就是首页那批真实数据。
 *
 * 只读：不改任何现有页面，也不往现有页面写状态。
 */
internal class ScreeningFeedStore(context: Context) {

    private val service = BaseService().also { it.setup(context) }

    /** 一次取数的结果。 */
    data class Page<T>(
        val items: List<T>,
        /** 下一页的 start；没有更多时为 null。 */
        val nextStart: Int?,
        val hasMore: Boolean,
        /** true = 这次没拿到真实数据，用的是随包样例。 */
        val fromSample: Boolean,
        /**
         * 这一页有没有真的发过请求。本地样片库这类页面不发请求，
         * 提示语要能说清「试过但没连上」和「本来就不走网络」的区别。
         */
        val requested: Boolean = true,
    )

    /** 首页的频道列表（电影 / 夜色 / 自定义）。 */
    suspend fun channels(category: FeedCategory, start: Int = 0): Page<RemoteEntry> =
        page(Constants.getUrlWithParam(category.api, start), category.fallback)

    /** 频道里的视频列表；`channel` 取自频道卡 actionUrl 里的 `channel=` 参数。 */
    suspend fun videos(channelParam: String, start: Int = 0, keyword: String = ""): Page<RemoteEntry> =
        page(
            Constants.getUrlWithParam(Constants.API_VIDEO_CHANNEL_LIST_URL, start, channelParam, keyword),
            emptyList(),
        )

    /** 图集列表（图片频道）。 */
    suspend fun albums(channelParam: String, start: Int = 0): Page<RemoteEntry> =
        page(Constants.getUrlWithParam(Constants.API_IMG_CHANNLE_URL, start, channelParam), emptyList())

    /** 图集内的图片。 */
    suspend fun albumImages(channelParam: String, start: Int = 0): Page<RemoteEntry> =
        page(Constants.getUrlWithParam(Constants.API_IMG_ITEMS_IMG_URL, start, channelParam, ""), emptyList())

    /**
     * 懒播放地址：`needLazyUrl` 的条目要再问一次才拿到真正可播的地址。
     * 与 `VideoShowActivityLazyUrl` 用同一份接口、同一条兜底（问不到就用原 actionUrl）。
     */
    suspend fun playUrl(item: RemoteEntry): String {
        val id = item.key.substringAfter(SERVER_KEY_PREFIX, "")
        if (!item.needsLazyUrl || id.isBlank()) return item.actionUrl
        val resolved = runCatching {
            val fetched = service.awaitGet(
                Constants.getUrlWithParam(Constants.API_VIDEO_ITEM_URL, id), RecommendVo::class.java, NET_TAG,
            )
            RecommendUrl.resolve(fetched.baseurl, fetched.actionUrl)
        }.getOrNull()
        return resolved?.takeIf { it.isNotBlank() } ?: item.actionUrl
    }

    /** 取一页并映射；请求失败或返回空都算「没数据」，回落样例而不是留一片空白。 */
    private suspend fun page(url: String, fallback: List<RemoteEntry>): Page<RemoteEntry> {
        val raw: InfoListVo<*>? = runCatching {
            service.awaitInfoList(url, RecommendVo::class.java, NET_TAG)
        }.getOrNull()
        val items = raw?.rows().orEmpty().mapIndexedNotNull { index, vo ->
            if (vo.dataType == RecommendVo.dataType_ADS || vo.actionUrl.isNullOrEmpty()) null
            else remoteEntry(
                id = vo.id,
                index = index,
                title = vo.title,
                description = vo.desc,
                // 服务端常把 img/actionUrl 写成相对路径，真正的站点在 baseurl
                imageUrl = RecommendUrl.resolve(vo.baseurl, vo.img),
                rate = vo.rate,
                actionUrl = RecommendUrl.resolve(vo.baseurl, vo.actionUrl),
                needsLazyUrl = vo.needLazyUrl == true,
                dataType = vo.dataType,
                param = vo.param,
            )
        }
        if (items.isEmpty()) return Page(fallback, nextStart = null, hasMore = false, fromSample = true)
        val more = raw!!.hasMore
        return Page(items, nextStart = if (more) raw.pageNum else null, hasMore = more, fromSample = false)
    }

    /**
     * 某个视频的服务端字幕列表。失败由调用方兜底展示，这里不吞：
     * 字幕是锦上添花，拉不到要能让页面说清「没取到」而不是装作没有。
     */
    suspend fun subtitles(videoId: String): List<SubtitleItem> {
        val raw: InfoListVo<SubtitleVo> = service.awaitInfoList(
            Constants.getUrlWithParam(Constants.API_SUBTITLE_LIST_URL, videoId, 0), SubtitleVo::class.java, NET_TAG,
        )
        return raw.voList.orEmpty().filter { it.id > 0L }.map { vo ->
            SubtitleItem(
                id = vo.id,
                title = vo.title?.takeIf { it.isNotBlank() } ?: "字幕 ${vo.id}",
                langLabel = subtitleLangLabel(vo.lang),
                formatLabel = subtitleFormat(vo.format, vo.title),
                sizeLabel = subtitleSizeLabel(vo.size),
            )
        }
    }

    /** 整份字幕文本（SRT / VTT）。内容字段在 JSON 信封里，过现有解密链路。 */
    suspend fun subtitleContent(subtitleId: Long): String? {
        val fetched = service.awaitGet(
            Constants.getUrlWithParam(Constants.API_SUBTITLE_CONTENT_URL, subtitleId),
            SubtitleContentVo::class.java, NET_TAG,
        )
        return fetched.content
    }

    private companion object {
        const val NET_TAG = "screening_feed"
    }
}

/** 服务端字幕列表项（Gson 直接映射，字段名即 JSON key）。 */
internal class SubtitleVo {
    @JvmField var id: Long = 0
    @JvmField var title: String? = null
    @JvmField var lang: String? = null
    @JvmField var format: String? = null
    @JvmField var size: Long = 0
}

/** 字幕内容接口的返回体。 */
internal class SubtitleContentVo {
    @JvmField var id: Long = 0
    @JvmField var content: String? = null
}

/**
 * `InfoListVo<RecommendVo>` 的解包：泛型在运行时拿不到具体元素类型，
 * 只在这里 cast 一次，调用方看到的始终是 `List<RecommendVo>`。
 */
@Suppress("UNCHECKED_CAST")
private fun InfoListVo<*>.rows(): List<RecommendVo> = voList as List<RecommendVo>
