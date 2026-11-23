package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `forceLevel` 是 P4 验收的每一档手动注入口（§12.3：「验收每一档都能手动触发」），
 * 通道是 adb 广播 `com.openapi.ks.chat.action.DEBUG_DEGRADE_LEVEL`。
 *
 * 规则用源码扫描钉住：
 * 1. 注入口存在且广播动作名固定（真机脚本、笔记都引用它）；
 * 2. 注册**必须**在 debug 判定之后——release 包里留一个能改降级档位的广播，
 *    等于把「形象冻结」这个开关交给任何能发广播的应用。
 */
class DegradeDebugInjectTest {

    private val root: File = File("..").canonicalFile
    private val transport: File = File(
        root,
        "feature-companion/src/main/java/com/openapi/ks/myapp/companion/LiveKitCompanionTransport.kt",
    )

    private fun registerFunBody(): String {
        val text = transport.readText()
        val start = text.indexOf("private fun registerDegradeDebugInject(")
        assertTrue("注入函数 registerDegradeDebugInject() 不存在", start >= 0)
        var depth = 0
        var end = -1
        for (i in text.indexOf('{', start)..text.lastIndex) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        return text.substring(start, if (end > start) end else text.length)
    }

    @Test
    fun `adb broadcast channel for degrade level injection exists`() {
        val body = registerFunBody()
        assertTrue("广播动作名必须是 DEBUG_DEGRADE_LEVEL", body.contains("DEBUG_DEGRADE_LEVEL"))
        assertTrue("必须调用 registerReceiver", body.contains("registerReceiver"))
        assertTrue("必须落到 degrade.forceLevel", body.contains("degrade.forceLevel"))
    }

    @Test
    fun `injection is debug-build only`() {
        val body = registerFunBody()
        val guard = Regex("""if\s*\(\s*!isDebugBuild\s*\)\s*return""").find(body)
        val reg = body.indexOf("registerReceiver")
        assertTrue("注册前必须有 if (!isDebugBuild) return 早退", guard != null)
        assertTrue("debug 早退必须在 registerReceiver 之前", guard!!.range.first < reg)
    }
}
