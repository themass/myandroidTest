package com.openapi.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.myapp.companion.CompanionStrings
import com.openapi.ks.myapp.companion.ResourceCompanionStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 陪伴页错误提示的**资源侧**契约（cleanup 项 2）。
 *
 * 这三句（现五条）以前是 `CompanionViewModel` 里的硬编码中文字面量，同提交里输入区那 7 条
 * 已经走资源，这几条是漏下的。现在接到 [CompanionStrings] 端口，于是与「对话」页
 * `ChatStringsResourceTest` 同款的两条保证必须钉住：
 * 1. zh 系统下措辞与迁移前的中文字面量**逐字一致**（老用户看到的没变）；
 * 2. 默认（英文）系统下不出现中日韩文字；
 * 3. 反射遍历端口的每个方法——端口加了方法却漏了某个语言，这里立刻红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CompanionStringsResourceTest {

    private fun strings(): CompanionStrings =
        ResourceCompanionStrings(ApplicationProvider.getApplicationContext<Context>())

    private fun allMessages(s: CompanionStrings): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in CompanionStrings::class.java.methods) {
            if (m.declaringClass == Any::class.java) continue
            val value = when (m.parameterCount) {
                0 -> m.invoke(s)
                1 -> m.invoke(s, 9)
                else -> error("CompanionStrings 出现了新的参数形状: $m")
            }
            out[m.name] = value as String
        }
        return out
    }

    private fun hasCjk(text: String) = text.any { it.code in 0x4E00..0x9FFF || it.code in 0x3000..0x303F }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun chineseSystemShowsTheExactChineseWordingMigratedFromTheLiterals() {
        val s = strings()
        // 逐字对照迁移前 ViewModel 里的中文字面量
        assertEquals("部分图片上传失败，已保留待重发", s.uploadPartialFailed())
        assertEquals("当前连接不支持图片，请重新进入对话后再试", s.uploadUnsupported())
        assertEquals("图片没能发送出去，已保留，请再按一次发送", s.attachmentDeliveryFailed())
        assertEquals("图片正在发送，稍等一下再发", s.uploadInProgress())
        // 占位符真的被替换（不是原样吐出 %1$d）
        assertEquals("一条消息最多 9 张图片", s.imageLimitReached(9))
        assertEquals("一条消息最多 3 张图片", s.imageLimitReached(3))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun everyPortMethodResolvesOnChineseLocales() {
        val messages = allMessages(strings())
        assertTrue("端口不该是空的", messages.size >= 5)
        for ((name, text) in messages) {
            assertTrue("zh 下 $name 不该为空", text.isNotBlank())
        }
    }

    @Test
    fun defaultLocaleHasNoChinese() {
        for ((name, text) in allMessages(strings())) {
            assertTrue("默认语言下 $name 出现了中文：「$text」", !hasCjk(text))
            assertTrue("默认语言下 $name 不该为空", text.isNotBlank())
        }
    }
}
