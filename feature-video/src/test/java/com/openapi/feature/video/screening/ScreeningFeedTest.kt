package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务端推荐位 → 放映场条目的映射规则。
 *
 * 这几个函数决定了瀑布流的比例、点进去去哪、以及进度存在哪个 key 上，
 * 一旦改错就是在真机上表现成「图被拉扁」「点进去是空的」，所以钉在单测里。
 */
class ScreeningFeedTest {

    @Test
    fun `rate 是长宽比，卡片要的是宽高比`() {
        // rate=2 表示 高是宽的 2 倍 → 竖长图，ratio = 0.5
        assertEquals(0.5f, feedRatio(2f), 0.001f)
        // rate=0.5 → 横图
        assertEquals(2f, feedRatio(0.5f), 0.001f)
    }

    @Test
    fun `没给比例时用正方，不会算出 0 或 NaN`() {
        assertEquals(1f, feedRatio(0f), 0.001f)
        assertEquals(1f, feedRatio(-1f), 0.001f)
    }

    @Test
    fun `极端比例被夹住，不至于把列表撑坏`() {
        assertEquals(0.5f, feedRatio(100f), 0.001f)
        assertEquals(2.5f, feedRatio(0.01f), 0.001f)
    }

    @Test
    fun `从 actionUrl 里取 channel 参数`() {
        assertEquals("movie_1", channelParamOf("videoList1://video/list?channel=movie_1&keyword="))
        assertEquals("img_night", channelParamOf("img://channel/list?channel=img_night"))
        // channel 不是第一个参数也要能取到
        assertEquals("sea", channelParamOf("videoList://video/list?keyword=x&channel=sea"))
    }

    @Test
    fun `channel 值是 URL 编码的先解开`() {
        assertEquals("夜色 频道", channelParamOf("img://channel/list?channel=%E5%A4%9C%E8%89%B2%20%E9%A2%91%E9%81%93"))
    }

    @Test
    fun `没有 channel 参数就给 null，不拿空串去拼接口`() {
        assertNull(channelParamOf("video://play?id=1"))
        assertNull(channelParamOf("videoList://video/list?channel="))
        assertNull(channelParamOf(""))
        assertNull(channelParamOf(null))
    }

    @Test
    fun `scheme 认得出图集和视频`() {
        assertEquals("img", schemeOf("img://channel/list?channel=x"))
        assertEquals("videolist1", schemeOf("videoList1://video/list?channel=x"))
        // https 直链不是自定义 scheme，但照样能认出来
        assertEquals("https", schemeOf("https://cdn.example.com/a.mp4"))
        assertNull(schemeOf("no-scheme-here"))
        assertNull(schemeOf(null))
    }

    @Test
    fun `稳定 id 优先服务端 id，其次地址，最后序号`() {
        assertEquals("srv:8123", feedKey(8123L, "video://x", 3))
        // id 缺失或非法（0/负数）时不能用它当身份，否则所有条目会共用同一份进度
        assertEquals("url:video://x", feedKey(null, "video://x", 3))
        assertEquals("url:video://x", feedKey(0L, "video://x", 3))
        assertEquals("idx:3", feedKey(null, null, 3))
    }

    @Test
    fun `没有 actionUrl 的推荐位直接丢，点不动的卡不该显示`() {
        assertNull(remoteEntry(id = 1L, index = 0, title = "有名字", description = null, imageUrl = null,
            rate = 0f, actionUrl = null, needsLazyUrl = false, dataType = 0))
        assertNull(remoteEntry(id = 1L, index = 0, title = "有名字", description = null, imageUrl = null,
            rate = 0f, actionUrl = "", needsLazyUrl = false, dataType = 0))
    }

    @Test
    fun `一条视频推荐位组装成能播的形状`() {
        val entry = remoteEntry(
            id = 42L, index = 0, title = "云层之上", description = " 去海拔更高的地方。 ",
            imageUrl = "https://cdn.example.com/a.jpg", rate = 2f,
            actionUrl = "https://api.example.com/vvv/api/video/item.json?id=42",
            needsLazyUrl = true, dataType = 0,
        )!!
        assertEquals("srv:42", entry.key)
        assertEquals("云层之上", entry.title)
        // 服务端 desc 常带首尾空白，卡片上不该留
        assertEquals("去海拔更高的地方。", entry.description)
        assertEquals("https://cdn.example.com/a.jpg", entry.imageUrl)
        assertEquals(0.5f, entry.ratio, 0.001f)
        assertTrue(entry.needsLazyUrl)
        assertNull(entry.channelParam)
        assertFalse(entry.isImageCard)
    }

    @Test
    fun `图集卡认 dataType 也认 scheme`() {
        assertTrue(remoteEntry(1L, 0, "册子", null, null, 0f, "img://channel/list?channel=n", false,
            dataType = 6)!!.isImageCard)
        // 老数据没标 dataType=6，但 scheme 是 img 也当图集处理
        assertTrue(remoteEntry(1L, 0, "册子", null, null, 0f, "img://items?channel=n", false,
            dataType = 0)!!.isImageCard)
    }

    @Test
    fun `图集内页用 param，不是 actionUrl 里的 channel`() {
        // 旧页面 ImgChannelImgListFragment 传的是 vo.param；两者不同时要保留 param
        val entry = remoteEntry(1L, 0, "册子", null, null, 0f, "img://items/list?channel=shown", false,
            dataType = 6, param = "real_key")!!
        assertEquals("shown", entry.channelParam)
        assertEquals("real_key", entry.param)
        assertEquals("real_key", entry.imageListKey())
    }

    @Test
    fun `param 空白视同没有，回落到 actionUrl 的参数`() {
        val entry = remoteEntry(1L, 0, "册子", null, null, 0f, "img://items/list?channel=shown", false,
            dataType = 6, param = "   ")!!
        assertNull(entry.param)
        assertEquals("shown", entry.imageListKey())
    }

    @Test
    fun `标题为空也有兜底文案，卡片不留空行`() {
        assertEquals("未命名", remoteEntry(1L, 0, null, null, null, 0f, "video://x", false, 0)!!.title)
        assertEquals("未命名", remoteEntry(1L, 0, "   ", null, null, 0f, "video://x", false, 0)!!.title)
    }

    @Test
    fun `首页图片卡的分页参数就是 actionUrl 里的 channel`() {
        val entry = remoteEntry(1L, 0, "夜色图集", null, null, 0f, "img://channel/list?channel=img_night", false,
            dataType = 6)!!
        assertEquals("img_night", entry.galleryChannelParam())
        assertNull(remoteEntry(1L, 0, "视频", null, null, 0f, "video://play?id=1", false, 0)!!.galleryChannelParam())
    }

    // ---- 真机回归：客户端调用口径必须和原页面一致（用户实机「网络问题」的根因）----

    @Test
    fun `外链卡认 http 和 https，其它 scheme 不算`() {
        assertTrue(isWebAction("https://exex.j8olo.cc/page/shipin/15"))
        assertTrue(isWebAction("http://a.example/x"))
        assertFalse(isWebAction("videoList1://video/list?channel=movie_1"))
        assertFalse(isWebAction("video://play?id=1"))
        assertFalse(isWebAction(null))
    }

    @Test
    fun `视频频道内页用 param：外链频道卡的真实标识在 param 里`() {
        // 服务端真实频道行：actionUrl 是外链不带 channel=，param 才有值（探针实测 53 个频道全这样）
        val entry = remoteEntry(1L, 0, "成人动漫", null, null, 1.2f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "se8成人动漫")!!
        assertNull(entry.channelParam)
        assertEquals("se8成人动漫", entry.videoChannelKey())
        // param 没有时退回 actionUrl 解析值（videoList1:// 频道卡）
        val inner = remoteEntry(2L, 0, "内部频道", null, null, 1f,
            "videoList1://video/list?channel=movie_1", false, dataType = 5)!!
        assertEquals("movie_1", inner.videoChannelKey())
        // 两个都没有就没有 key：调用方不许拿 null/空串去发 items.json
        assertNull(remoteEntry(3L, 0, "直链", null, null, 1f, "video://play?id=1", false, 0)!!.videoChannelKey())
    }

    @Test
    fun `外链卡路由：有频道 key 进内页吸收，没有的才交回 WEB`() {
        // 外链频道卡（param 有真频道 key）：B 线吸收，进放映场内页。
        // 探针实测 items.json 认这批 key（hsexhsex.men 返回 30 条可播条目），
        // 查不到行的频道（se8成人动漫）也返回 errno0 + 空列表，由内页诚实空态兜底。
        val channel = remoteEntry(1L, 0, "成人动漫", null, null, 1.2f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "hsexhsex.men")!!
        assertEquals(FeedRoute.CHANNEL, routeOf(channel))
        // 原页面里这类卡点下去是 WebView；旧分流让用户「往里点就掉旧 UI」的根因就在这
        // 内部频道卡一直是 CHANNEL，不受影响
        assertEquals(FeedRoute.CHANNEL, routeOf(remoteEntry(2L, 0, "内部频道", null, null, 1f,
            "videoList1://video/list?channel=movie_1", false, dataType = 5)!!))
        // 拿不出频道 key 的纯网页还是 WEB：内页没接口可发，不许发空 channel=
        assertEquals(FeedRoute.WEB, routeOf(remoteEntry(3L, 0, "网页视频页", null, null, 1f,
            "https://ddys.io/movie/2024.html", false, dataType = 2)!!))
        // 网页壳不播 HLS/MKV；内核也不认的 .flv 继续走 WEB，比进播放器黑屏强
        assertEquals(FeedRoute.WEB, routeOf(remoteEntry(6L, 0, "flv 流", null, null, 1f,
            "https://cdn.example/a.flv", false, dataType = 2)!!))
        assertEquals(FeedRoute.ALBUM, routeOf(remoteEntry(4L, 0, "册子", null, null, 1f,
            "img://channel/list?channel=img_night", false, dataType = 6)!!))
    }

    // ---- ddys 片单真机回归：影片卡 actionUrl 是裸 m3u8，必须进内核而不是 WebView ----

    @Test
    fun `媒体文件外链直接播，普通网页外链进 WEB`() {
        assertTrue(isPlayableMediaUrl("https://v.lzcdn27.com/20260110/3188_12fe1643/index.m3u8"))
        assertTrue(isPlayableMediaUrl("https://cdn.example/A.MP4"))
        assertTrue(isPlayableMediaUrl("https://cdn.example/a.m3u8?token=xyz"))
        // query 里有 .mp4 不算，看的是路径
        assertFalse(isPlayableMediaUrl("https://cdn.example/play?id=a.mp4"))
        assertFalse(isPlayableMediaUrl("https://ddys.io/movie/2024.html"))
        assertFalse(isPlayableMediaUrl("https://ddys.io/movie/2024"))
        assertFalse(isPlayableMediaUrl("videoList1://list?channel=ddys动作"))
        assertFalse(isPlayableMediaUrl(null))
    }

    @Test
    fun `裸 m3u8 影片卡路由 PLAY：不进 WebView 也不当频道入口`() {
        // 实机日志样本：route=WEB 导致黑屏（WebView 不播 HLS）
        val movie = remoteEntry(1L, 0, "猎战狂徒", null, null, 1.5f,
            "https://v.lzcdn27.com/20260110/3188_12fe1643/index.m3u8", false, dataType = 2)!!
        assertEquals(FeedRoute.PLAY, routeOf(movie))
        // 不是「还有一页」：右上角也不能显示成频道入口样式
        assertFalse(movie.isGroupEntry)
    }

    @Test
    fun `外链频道卡被内页吸收后仍是入口，且留网页兜底`() {
        // 外链频道卡（param 有值、路径不是媒体文件）：进内页，右上角保持入口样式
        val channel = remoteEntry(1L, 0, "成人动漫", null, null, 1.2f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "se8成人动漫")!!
        assertEquals(FeedRoute.CHANNEL, routeOf(channel))
        assertTrue(channel.isGroupEntry)
        // 内页取不到片单时的兜底：这条原本是网页，页面要能「在网页里打开」
        assertEquals("https://exex.j8olo.cc/page/shipin/15", channel.webFallbackUrl())
        // 内部频道卡没有网页可回退：兜底是 null，页面不许瞎放按钮
        val inner = remoteEntry(2L, 0, "内部频道", null, null, 1f,
            "videoList1://video/list?channel=movie_1", false, dataType = 5)!!
        assertEquals(FeedRoute.CHANNEL, routeOf(inner))
        assertNull(inner.webFallbackUrl())
    }

    @Test
    fun `外链 param 是网页地址就不算频道 key`() {
        // 探针实测：items.json 的条目行 param 常是网页地址（https://hsex.men/video-1259457.htm），
        // 说的是「这条的网页在哪」，不是「这是个频道」。拿它发 items.json 只会查到 0 行。
        val row = remoteEntry(1L, 0, "猎战狂徒", null, null, 1f,
            "https://bad.news/t/5806345", false, dataType = 5, param = "https://bad.news/t/5806345")!!
        assertNull(row.videoChannelKey())
        // 于是它不是频道入口，是网页：routeOf 交回 WEB（旧页面本来就是 WebView）
        assertEquals(FeedRoute.WEB, routeOf(row))
        // 真频道 key（标签串，不是 URL）照旧算数
        val card = remoteEntry(2L, 0, "成人动漫", null, null, 1f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "hsexhsex.men")!!
        assertEquals("hsexhsex.men", card.videoChannelKey())
    }

    // ---- 播放区横滑快进退：方向与档位是纯函数，钉住「右滑快进 30s 一档」 ----

    @Test
    fun `横滑 seek 右滑快进左滑快退，每四分之一屏一档 30 秒`() {
        val w = 1080f
        assertEquals(0L, dragSeekMs(0f, w))
        // 滑过 1/4 屏宽 = 一档
        assertEquals(30_000L, dragSeekMs(300f, w))
        assertEquals(30_000L, dragSeekMs(400f, w))
        // 半屏 = 两档
        assertEquals(60_000L, dragSeekMs(540f, w))
        // 左滑快退，负方向
        assertEquals(-30_000L, dragSeekMs(-270f, w))
        assertEquals(-120_000L, dragSeekMs(-1080f, w))
        // 还没过半档（12.5%）不算；尺寸非法不炸
        assertEquals(0L, dragSeekMs(-100f, w))
        assertEquals(0L, dragSeekMs(500f, 0f))
    }

    // ---- 夜色分类真机回归：video://config?channel= 是配置频道，不是可播条目 ----

    @Test
    fun `video 带 channel 参数的算配置频道，没有的不算`() {
        assertTrue(isConfigAction("video://config?channel=hsex_all"))
        // 播放直链不带 channel：还是直接播
        assertFalse(isConfigAction("video://play?id=1"))
        assertFalse(isConfigAction("video://config?channel="))
        assertFalse(isConfigAction("https://ddys.io/movie?genre=other"))
        assertFalse(isConfigAction(null))
    }

    @Test
    fun `夜色配置频道卡被内页吸收：进内页列子频道，不发片单接口`() {
        // 夜色分类实测全是这种卡。hsex_all 只是 channelType 列：拿它发 items.json 是 0 行，
        // 发 channel.json 才列得出子频道（探针实测 18禁 / hsex.men 两行）。
        // 所以吸收的正确姿势不是「内页直接取片单」，而是内页先列子频道（SUBCHANNELS）。
        val cfg = remoteEntry(1L, 0, "hsex最好的自拍", null, null, 1f,
            "video://config?channel=hsex_all", false, dataType = 5)!!
        assertEquals(FeedRoute.CHANNEL, routeOf(cfg))
        // 它是入口不是直播：isGroupEntry 必须为真，否则会被判给 PLAY
        assertTrue(cfg.isGroupEntry)
        assertEquals(ScreeningPageKind.SUBCHANNELS, videoChannelPageKind(cfg))
        // 图集判定优先：dataType=6 就算带 channel 也先进图集
        assertEquals(FeedRoute.ALBUM, routeOf(remoteEntry(2L, 0, "册子", null, null, 1f,
            "video://config?channel=x", false, dataType = 6)!!))
    }

    @Test
    fun `普通频道页取片单，只有配置频道取子频道`() {
        // 外链频道卡（param 有真 key）：内页直接 items.json
        assertEquals(ScreeningPageKind.VIDEOS, videoChannelPageKind(remoteEntry(1L, 0, "成人动漫", null, null, 1f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "hsexhsex.men")!!))
        // 内部频道卡同样取片单
        assertEquals(ScreeningPageKind.VIDEOS, videoChannelPageKind(remoteEntry(2L, 0, "内部频道", null, null, 1f,
            "videoList1://video/list?channel=movie_1", false, dataType = 5)!!))
    }

    @Test
    fun `子频道行能继续下钻成片单页`() {
        // channel.json 实测行：actionUrl 是相对路径（baseurl 拼完变外链），param 是子频道标签。
        // 点它应当再开一层新内页（items.json 发子频道 key），不是掉回旧页面。
        val sub = remoteEntry(1L, 0, "hsex.men", null, null, 1f,
            "https://hsex.tv/list-1.htm", false, dataType = 5, param = "hsexhsex.men")!!
        assertEquals(FeedRoute.CHANNEL, routeOf(sub))
        assertEquals(ScreeningPageKind.VIDEOS, videoChannelPageKind(sub))
    }

    // ---- 协议分发全表回归（用户报「全被识别成视频列表」，对着 ConfigActionJump 的表钉） ----

    @Test
    fun `teleplay 和 movie 走子频道层，不发片单接口`() {
        // 旧路由：teleplay:// → TeleplayActivity（内部就是 VideoChannleBodyFragment）、
        // movie:// → MovieChannleActivity，第一层都发 /video/channel.json。
        // 放映场以前只认 video://，这两种被发去 items.json（channelType 值查不到行）。
        assertTrue(isConfigAction("teleplay://config?channel=tv_all"))
        assertTrue(isConfigAction("movie://config?channel=dy_all"))
        val tv = remoteEntry(1L, 0, "teleplay", null, null, 1f, "teleplay://config?channel=tv_all", false, dataType = 5)!!
        assertEquals(FeedRoute.CHANNEL, routeOf(tv))
        assertEquals(ScreeningPageKind.SUBCHANNELS, videoChannelPageKind(tv))
        val movie = remoteEntry(2L, 0, "movie", null, null, 1f, "movie://config?channel=dy_all", false, dataType = 5)!!
        assertEquals(ScreeningPageKind.SUBCHANNELS, videoChannelPageKind(movie))
        // 不带 channel 的直链不算配置频道（和 video:// 同规则）
        assertFalse(isConfigAction("movie://play?id=1"))
    }

    @Test
    fun `sound 和 text 交回原分发，不进视频内页`() {
        // sound:// 仍交回 SoundChannleActivity；text:// 已进 FeedRoute.TEXT（ScreeningText）。
        assertFalse(isLegacyOnlyAction("sound://config?channel=nvyou"))
        assertFalse(isLegacyOnlyAction("text://config?channel=小说"))
        assertFalse(isLegacyOnlyAction("video://config?channel=nvyou"))
        val sound = remoteEntry(7L, 0, "好听的音乐", null, null, 1f, "sound://config?channel=nvyou", false, dataType = 5)!!
        assertEquals(FeedRoute.SOUND, routeOf(sound))
        val text = remoteEntry(11L, 0, "好看的文字", null, null, 1f, "text://config?channel=小说", false, dataType = 5)!!
        assertEquals(FeedRoute.TEXT, routeOf(text))
    }

    @Test
    fun `videoList 和 videoList1 走片单层（和旧 VideoListShowActivity 同一接口）`() {
        // 旧路由把 videoList/videoList1 都交 VideoListShowActivity → /video/channel/items.json，
        // 没有子频道层。放映场对它们就该 VIDEOS；判错成 SUBCHANNELS 反而查空。
        val vl = remoteEntry(2L, 0, "testlist", null, null, 1f, "videoList://config?channel=testlist", false, dataType = 5)!!
        assertEquals(FeedRoute.CHANNEL, routeOf(vl))
        assertEquals(ScreeningPageKind.VIDEOS, videoChannelPageKind(vl))
        val vl1 = remoteEntry(3L, 0, "电影分类", null, null, 1f, "videoList1://video/list?channel=movie_1", false, dataType = 5)!!
        assertEquals(ScreeningPageKind.VIDEOS, videoChannelPageKind(vl1))
    }

    @Test
    fun `img 图集卡优先进图集层（img channel 复用相册接口族）`() {
        // img:// 的 /img/channel.json + /img/items/img.json 就是放映场相册层的同一接口族，
        // 已在 ALBUM 分支接住（这条钉住顺序：scheme=img 的 dataType 兜底不能让 sound/text 误进图集）。
        val img = remoteEntry(8L, 0, "好看的图片", null, null, 1f, "img://config?channel=porn_sex", false, dataType = 5)!!
        assertEquals(FeedRoute.ALBUM, routeOf(img))
    }
}

    @Test
    fun `text config 走 TEXT 路由`() {
        assertTrue(isTextConfigAction("text://config?channel=小说"))
        assertFalse(isTextConfigAction("text://play?id=1"))
    }

    @Test
    fun `sound config 走 SOUND 路由`() {
        assertTrue(isSoundConfigAction("sound://config?channel=nvyou"))
        assertFalse(isSoundConfigAction("sound://play?id=1"))
    }
