package com.openapi.feature.video.screening

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40 分享入口（用户 2026-11-26：声音馆/放映场电影/图集阅览里都找不到分享）。
 *
 * 决策层只有一条：电影那条**分享的地址**取片子的身份地址（actionUrl），
 * 不是播放器正在用的那条 uri——懒播放地址每次可能不一样（带时效签名），
 * 发给别人等于发一张过期的链接。没有身份地址才退回 uri。
 * 四个入口（竖屏顶栏/全屏顶栏/声音条/阅览底栏）用结构钉子钉住：
 * 少接一处，用户就在这一页找不到分享。
 */
class ScreeningShareEntriesTest {

    @Test
    fun `电影分享优先身份地址，懒播放 uri 只当兜底`() {
        // 字符串入参那条是决策本体：Uri 在 JVM 单测里是 stub（parse 返回 null），
        // 只测 ScreeningSource 那条会把兜底分支测成假通过。
        assertEquals("https://site/v/9", screeningShareUrl("https://site/v/9", "https://cdn/x.mp4?sig=1"))
        assertEquals("https://site/v/9", screeningShareUrl("https://site/v/9", null))
        // 没有身份地址（本机文件等）：退回当前 uri，仍要说得出这是个链接。
        assertEquals("file:///sdcard/a.mp4", screeningShareUrl(null, "file:///sdcard/a.mp4"))
        // 两个都没有：空串，入口自己决定不显示（不分享一个空文本）。
        assertEquals("", screeningShareUrl(null, null))
        assertEquals("", screeningShareUrl("  ", ""))
        // ScreeningSource 这条把 uri 拆成字符串再走同一决策。
        val withIdentity = ScreeningSource("片名", "", uri = Uri.parse("https://cdn/x.mp4?sig=1"), shareUrl = "https://site/v/9")
        assertEquals("https://site/v/9", screeningShareUrl(withIdentity))
    }

    @Test
    fun `四个分享入口都在位`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        val playback = File(dir, "ScreeningPlayback.kt").readText()
        // 竖屏顶栏与全屏顶栏各一处，共用同一条 shareText（第一版都发链接文本）。
        assertEquals("电影两条顶栏各一个分享入口", 2, Regex("""shareText\(""").findAll(playback).count())
        val sound = File(dir, "ScreeningSound.kt").readText()
        assertTrue("声音条要有分享（与收藏并排）", Regex("""shareText\(""").containsMatchIn(sound))
        val gallery = File(dir, "ScreeningGallery.kt").readText()
        assertTrue("阅览底栏要有分享", Regex("""shareText\(""").containsMatchIn(gallery))
    }

    @Test
    fun `电影分享地址装配自服务端身份地址`() {
        val activity = File("src/main/java/com/openapi/feature/video/screening/ScreeningActivity.kt").readText()
        assertTrue("ScreeningSource 装配要带 shareUrl=entry.actionUrl",
            Regex("""shareUrl = entry\.actionUrl""").containsMatchIn(activity))
    }
}
