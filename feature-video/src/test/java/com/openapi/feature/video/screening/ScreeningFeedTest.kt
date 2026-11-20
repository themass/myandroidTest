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
    fun `外链卡路由到 WEB，不进播放器也不发空 channel`() {
        val web = remoteEntry(1L, 0, "成人动漫", null, null, 1.2f,
            "https://exex.j8olo.cc/page/shipin/15", false, dataType = 5, param = "se8成人动漫")!!
        assertEquals(FeedRoute.WEB, routeOf(web))
        // 原页面里这类卡点下去是 WebView；放映场不许把它判给 CHANNEL/PLAY
        assertEquals(FeedRoute.CHANNEL, routeOf(remoteEntry(2L, 0, "内部频道", null, null, 1f,
            "videoList1://video/list?channel=movie_1", false, dataType = 5)!!))
        // 直链 mp4 也是 https scheme：原路由表对 https 只有 WebView 一个去处，跟它对齐
        assertEquals(FeedRoute.WEB, routeOf(remoteEntry(3L, 0, "直链视频", null, null, 1f,
            "https://cdn.example/a.mp4", false, dataType = 2)!!))
        assertEquals(FeedRoute.ALBUM, routeOf(remoteEntry(4L, 0, "册子", null, null, 1f,
            "img://channel/list?channel=img_night", false, dataType = 6)!!))
    }
}
