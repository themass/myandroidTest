package com.openapi.feature.chat.data.mapper

import com.openapi.feature.chat.data.mapper.ChatLogMapper.TYPE_IMAGE
import com.openapi.feature.chat.data.mapper.ChatLogMapper.WHO_SENT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A43-5 的观测钉（协调会话要求：「历史图集可能塌缩成 1 张——加一条日志或测试观测」）：
 * 历史图 300 秒签名过期只是「旧的滑不动」的一条路径；另一条是图集**塌缩**——
 * ChatLogMapper.readImages 会**静默丢掉** content 为空的条目（旧数据/半途写入），
 * 3 张的历史行读回来可能只剩 1 张；而 mediaview MediaStack 在 count==1 时**压根不挂滑动手势**
 * （单张没有位置可言）。用户侧观感同样是「这组旧图滑不动」，但根因不同、修法不同（属数据侧），
 * 这里把当前行为钉成观测：谁改 readImages 的丢弃规则（或给 count==1 挂手势），这条会第一时间变红。
 * org.json 在 JVM 是 stub，走 Robolectric（同目录先例）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChatLogMapperImageCollapseObservationTest {

    private fun readImages(rawContent: String) = ChatLogMapper.toDomain(
        chatId = "m1",
        who = WHO_SENT,
        msgType = TYPE_IMAGE,
        rawContent = rawContent,
        createdAtMillis = 1L,
    )!!.images

    @Test
    fun emptyContentEntriesAreSilentlyDroppedAndTheGalleryShrinks() {
        // 三条里两条 content 为空 → 读回来只剩两条：这就是「3 张塌缩」的发生方式
        val images = readImages(
            """{"content":"remote","images":[{"content":"remote","uploadId":"u1"},{"content":""},{"content":"/data/local/c.jpg"}]}""",
        )
        assertEquals("content 为空的条目被丢掉（观测：这是历史图集塌缩到 1 张的前半程）", 2, images.size)
        assertEquals("", images[0].path) // REMOTE 标记槽保留（失败占位靠它）
        assertEquals("/data/local/c.jpg", images[1].path)
    }

    @Test
    fun aSingleSurvivingEntryMeansNoSwipeGestureAtAll() {
        // 塌缩到 1 张：MediaStack count==1 不挂手势——「旧的滑不动」的另一半解释。
        // 这里钉数据侧确实会产出单条：UI 侧（remember(count) 修复）无法救 count 真变小的情况。
        val images = readImages("""{"content":"remote","images":[{"content":"/data/local/only.jpg"}]}""")
        assertEquals(1, images.size)
    }

    @Test
    fun remoteWithoutUploadIdSurvivesAsEmptyPathSlot() {
        // content=="remote" 且没有 uploadId：**不**被丢（path 空保留，换源无从查起但占位在）。
        // 协调会话取证里「uploadId 缺失→path 空被丢」的口径以这条为准：丢的只有 content 为空的条目。
        val images = readImages("""{"content":"remote","images":[{"content":"remote"}]}""")
        assertEquals(1, images.size)
        assertEquals("", images.single().path)
        assertEquals(null, images.single().uploadId)
    }
}
