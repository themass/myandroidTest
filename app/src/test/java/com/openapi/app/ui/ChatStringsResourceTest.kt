package com.openapi.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openapi.feature.chat.presentation.ChatStrings
import com.openapi.feature.chat.presentation.ResourceChatStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对话页错误文案的**资源侧**契约（整改清单 P2-14）。
 *
 * P2-14a 把 feature-chat 的字符串改成「values/ 英文、values-zh/ 中文」，并删掉了
 * app 里遮蔽它们的 73 个副本（资源合并顺序 core → feature-chat → app，app 那批
 * 一直是实际生效的）；P2-14b 把 presentation 层的硬编码错误文案接到 ChatStrings 端口。
 * feature-chat 自己开不了 includeAndroidResources（库模块引用 :app 主题），
 * 所以文案内容只能在 :app 里钉。
 *
 * 钉三件事：
 * 1. zh 系统：错误文案与迁移前的中文字面量逐字一致（老用户看到的没变）；
 * 2. 默认（英文）系统：这些键一律不出现中日韩文字——这正是这次迁移的目的；
 *    曾经遮蔽在 app 里的中文 UI 键（ui_cancel 等）也一样落到英文。
 * 3. 反射遍历 ChatStrings 的每个方法：端口加新方法却忘了实现/漏了某个语言，这里立刻红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChatStringsResourceTest {

    private fun strings(): ChatStrings =
        ResourceChatStrings(ApplicationProvider.getApplicationContext<Context>())

    /** 反射调用端口的全部方法（带 Int 参数的用 2），返回 「方法名 → 文案」。 */
    private fun allMessages(s: ChatStrings): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in ChatStrings::class.java.methods) {
            if (m.declaringClass == Any::class.java) continue
            val value = when (m.parameterCount) {
                0 -> m.invoke(s)
                1 -> m.invoke(s, 2)
                else -> error("ChatStrings 出现了新的参数形状: $m")
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
        // 逐字对照迁移前 presentation 层里的中文字面量（改动任何一条都要过这里）
        assertEquals("加载更早的消息失败，请稍后重试", s.historyLoadOlderFailed())
        assertEquals("已停止，消息未发出，可点重试", s.stoppedNoReply())
        assertEquals("没有收到回复，请重试", s.emptyReply())
        assertEquals("回复失败，请重试", s.replyFailed())
        assertEquals("回复没有完整收到，请重试", s.replyTruncated())
        assertEquals("删除失败，请稍后重试", s.deleteFailed())
        assertEquals("读取消息失败，请稍后重试", s.readMessagesFailed())
        assertEquals("1 条尚未同步，请稍后再删", s.deleteManyPartUnsynced(1))
        assertEquals("2 条删除失败", s.deleteManyPartFailed(2))
        assertEquals("，", s.errorPartSeparator())
        // 端口里「失败 N 条」拼接后的整句，与旧实现逐字一致
        assertEquals(
            "2 条删除失败，1 条尚未同步，请稍后再删",
            listOf(s.deleteManyPartFailed(2), s.deleteManyPartUnsynced(1))
                .joinToString(s.errorPartSeparator()),
        )
        assertEquals("有 2 条消息清空失败，请稍后重试", s.clearFailed(2))
        assertEquals("创建失败：名称不能为空、不能与已有会话重名，或网络异常", s.sessionCreateFailed())
        assertEquals("重命名失败：名称不能为空，或网络异常", s.sessionRenameFailed())
        assertEquals("读取附件失败，请重试", s.attachmentReadFailed())
        assertEquals("读取图片失败，请重试", s.imageReadFailed())
        assertEquals("编辑失败，已使用原图", s.imageEditFallback())
        assertEquals("朗读失败，请稍后重试", s.speakFailed())
        assertEquals("切换角色失败，请稍后重试", s.roleSwitchFailed())
        assertEquals("创建角色失败，请稍后重试", s.roleCreateFailed())
        assertEquals("更新角色失败，请稍后重试", s.roleUpdateFailed())
        assertEquals("删除角色失败，请稍后重试", s.roleDeleteFailed())
        // 终评 I-1：图片超上限的那句提示是**用户看得见**的（选择器 maxItems 只是建议值，
        // 厂商 ROM 可能不认，所以本地还有一道闸会走到这句）。数字是填进去的，
        // 所以这里同时钉住「占位符真的被替换了」而不是原样吐出 %1$d。
        assertEquals("一条消息最多 9 张图片", s.imageLimitReached(9))
        assertEquals("一条消息最多 3 张图片", s.imageLimitReached(3))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun everyPortMethodResolvesOnChineseLocales() {
        val messages = allMessages(strings())
        assertTrue("端口不该是空的", messages.size >= 21)
        for ((name, text) in messages) {
            assertTrue("zh 下 $name 不该为空", text.isNotBlank())
        }
    }

    /**
     * 默认语言（这里取 en，未列出翻译时系统回退 values/）：整个端口不允许出现任何
     * CJK 字符——包括分隔符。这是 P2-14 的验收面：英文设备不再看到中文。
     */
    @Test
    @Config(qualifiers = "en")
    fun englishSystemNeverSeesChineseInTheErrorStrings() {
        for ((name, text) in allMessages(strings())) {
            assertFalse("默认语言下 $name 返回了中文: $text", hasCjk(text))
        }
    }

    /**
     * P2-14a 的合并面：feature-chat 的英文默认要赢过 core 里同名的中文键
     * （app/build.gradle 依赖顺序 core → feature-chat）。曾经 app 里还有一份中文遮蔽。
     */
    @Test
    @Config(qualifiers = "en")
    fun englishSystemGetsEnglishUiStringsFromFeatureChatNotCoreChinese() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals("Cancel", res.getString(com.openapi.feature.chat.R.string.ui_cancel))
        assertEquals("All", res.getString(com.openapi.feature.chat.R.string.ui_all))
        assertEquals("Retry", res.getString(com.openapi.feature.chat.R.string.ui_retry))
    }
}
