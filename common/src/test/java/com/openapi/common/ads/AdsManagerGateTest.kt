package com.openapi.common.ads

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AdsManager 里凡是解引用广告实现表（`xxxMap.get(...)`）的方法，都必须先过 [AdsGate]。
 *
 * 真机踩到的第二种崩法（P2-13，2026-11-21）：给 show* 加了 gate、漏了
 * `exitSplashAds`，debug 包 init() 提前返回，表是空的，
 * LaunchActivity.onDestroy 调 exitSplashAds 直接 NPE，冷启动弹崩溃页。
 *
 * 成对出现是这类代码的固有形状：showX/exitX 操作同一张表，show 被挡住时
 * exit 同样无对象可操作。漏一个就是线上崩溃，所以用源码扫描钉住整条规则。
 */
class AdsManagerGateTest {

    private val src: File =
        File("..").canonicalFile.resolve("common/src/main/java/com/openapi/common/ads/base/AdsManager.java")

    @Test
    fun `every method touching the ads registries is behind AdsGate`() {
        val bodies = methodsTouchingMaps()
        assertTrue("没找到任何方法，扫描方式可能失效了", bodies.isNotEmpty())
        val ungated = bodies.filter { (_, body) -> !body.contains("AdsGate.enabled()") }
        assertTrue(
            "这些方法会解引用广告实现表却没先过 AdsGate，debug 包（init 被挡、表为空）会 NPE：" +
                ungated.keys.joinToString(", ") { it + "()" },
            ungated.isEmpty(),
        )
    }

    /** 方法签名 → 方法体（花括号配平）。只收方法体内出现 `xxxMap.get(` 的。 */
    private fun methodsTouchingMaps(): Map<String, String> {
        val text = src.readText()
        val out = LinkedHashMap<String, String>()
        val sig = Regex("""(?:public|private|protected)[\w\s<>,\[\]]*?\s(\w+)\s*\([^)]*\)\s*\{""")
        for (m in sig.findAll(text)) {
            val start = m.range.last
            var depth = 0
            var end = -1
            for (i in start..text.lastIndex) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) { end = i; break }
                    }
                }
            }
            val body = text.substring(start, if (end > start) end else text.length)
            if (Regex("""\w+Map\.get\(""").containsMatchIn(body)) out[m.groupValues[1]] = body
        }
        return out
    }
}
