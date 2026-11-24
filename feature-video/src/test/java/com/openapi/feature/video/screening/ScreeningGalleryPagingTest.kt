package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins image paging and full resolution behavior in the Lightbox. */
class ScreeningGalleryPagingTest {

    private fun shot(actionUrl: String, imageUrl: String? = "https://img.test/thumb.jpg") =
        RemoteEntry(
            key = "shot",
            title = "一张",
            description = "",
            imageUrl = imageUrl,
            ratio = 1f,
            actionUrl = actionUrl,
            needsLazyUrl = false,
            channelParam = null,
            isImageCard = false,
        )

    @Test
    fun `全屏大图优先 actionUrl 原图，没有才退回缩略 imageUrl`() {
        assertEquals("https://img.test/full.jpg", lightboxImageUrl(shot("https://img.test/full.jpg")))
        assertEquals("https://img.test/thumb.jpg", lightboxImageUrl(shot("", "https://img.test/thumb.jpg")))
        assertNull(lightboxImageUrl(shot("", null)))
    }

    @Test
    fun `续取 key 是当前张的 actionUrl，空地址不发无意义的请求`() {
        assertEquals("https://img.test/full.jpg", galleryTailItemUrl(shot("https://img.test/full.jpg")))
        assertNull(galleryTailItemUrl(shot("")))
        assertNull(galleryTailItemUrl(null))
    }

    @Test
    fun `store 的续取方法走 img item page 接口，参数顺序跟旧页一致`() {
        val store = File("src/main/java/com/openapi/feature/video/screening/ScreeningFeedStore.kt").readText()
        val body = bodyOf(store, "suspend fun imageItemPage(")
        assertTrue("imageItemPage 必须走 API_IMG_ITEM_PAGE_URL", body.contains("Constants.API_IMG_ITEM_PAGE_URL"))
        assertTrue("参数顺序为 start、itemUrl",
            Regex("""API_IMG_ITEM_PAGE_URL,\s*start,\s*itemUrl""").containsMatchIn(body))
    }

    @Test
    fun `Lightbox 尾部触发续取且册内网格保留缩略图接口`() {
        val text = File("src/main/java/com/openapi/feature/video/screening/ScreeningGallery.kt").readText()
        val gallery = bodyOf(text, "internal fun ScreeningGallery(")
        assertTrue(gallery.contains("store.albumImages("))
        assertTrue(gallery.contains("store.imageItemPage("))
        assertTrue(gallery.contains("galleryTailItemUrl("))
        val lightbox = bodyOf(text, "private fun Lightbox(")
        assertTrue(lightbox.contains("let(onTail)"))
        assertFalse(lightbox.contains("EntryCover("))
        assertTrue(lightbox.contains("lightboxImageUrl(shot)"))
        val grid = bodyOf(text, "private fun AlbumContents(")
        assertTrue(grid.contains("EntryCover(shot,"))
        assertFalse(grid.contains("imageItemPage"))
    }

    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue("找不到 $signature", start >= 0)
        val next = Regex("\n(internal |private |@)\\S|\\nobject |\\nclass ").find(text, start + signature.length)
        return text.substring(start, next?.range?.first ?: text.length)
    }
}
