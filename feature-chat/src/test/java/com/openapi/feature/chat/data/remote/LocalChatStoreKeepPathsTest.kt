package com.openapi.feature.chat.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openapi.core.db.AppDatabase
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 复审 Important 的数据侧：`localAttachmentPathsInUse` 必须**从库里**把「还不能删」的
 * 附件路径捞出来，但**不限会话**——暂存目录不按会话分。
 *
 * 判据**不等于** [LocalChatStore.localPending]（与 nit 3 改真后的生产注释同一口径）：
 * 这条钉的是「`local-` 前缀 + 非 SENT」；`localPending` 还要 `role == USER`，
 * 这里不筛 role。下面的用例都在这个前提下读——`local-user-*` 只是最典型的样本，
 * 助手指派与 voice 行同样进白名单（最坏是过留一轮回收，见 helper 注释）。
 *
 * 这里必须用真 Room（in-memory）而不是假仓库：投递状态存在 content 的 JSON 里，
 * 「SENT 的行不进白名单」这一条只有真正往返一次写读才钉得住；
 * 而 JVM 上没有 org.json 的 Android 桩跑不通（返回 null → 假绿），所以 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalChatStoreKeepPathsTest {

    private lateinit var db: AppDatabase
    private lateinit var store: LocalChatStore

    @Before
    fun open() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = LocalChatStore(context, db)
    }

    @After
    fun close() = db.close()

    private fun imageMessage(
        id: String,
        sessionId: Long,
        paths: List<String>,
        delivery: DeliveryStatus,
        remotePaths: List<String> = emptyList(),
    ) = ChatMessage(
        id = id,
        role = ChatRole.USER,
        text = "看图",
        createdAtMillis = 1_700_000_000_000L,
        kind = MessageKind.IMAGE,
        images = paths.map { ImageAttachment(path = it) } +
            remotePaths.map { ImageAttachment(path = it, source = ImageSource.REMOTE, uploadId = "u-$it") },
        files = listOf(FileAttachment(path = "/tmp/file-$id.txt", name = "f.txt", sizeBytes = 1L)),
        delivery = delivery,
    )

    @Test
    fun `paths of a non-SENT local row are kept, and a SENT row contributes nothing`() {
        store.persist(1L, imageMessage("local-user-1", 1L, listOf("/tmp/a/img_1.jpg"), DeliveryStatus.FAILED))
        store.persist(1L, imageMessage("local-user-2", 1L, listOf("/tmp/a/img_2.jpg"), DeliveryStatus.SENT))

        val kept = store.localAttachmentPathsInUse()

        assertTrue("没送达那条的字节必须在白名单里（重发靠它）", kept.contains("/tmp/a/img_1.jpg"))
        assertFalse("已 SENT 那条不算引用（服务端历史有它，字节是孤儿）", kept.contains("/tmp/a/img_2.jpg"))
        assertTrue("文件附件一起算", kept.contains("/tmp/file-local-user-1.txt"))
    }

    @Test
    fun `a non-SENT row in another session is still kept`() {
        // 暂存目录是全 app 一个，会话切换后另一段对话里没发出去的那条还在引用它的字节。
        store.persist(2L, imageMessage("local-user-9", 2L, listOf("/tmp/b/img_9.jpg"), DeliveryStatus.SENDING))

        assertTrue(store.localAttachmentPathsInUse().contains("/tmp/b/img_9.jpg"))
    }

    @Test
    fun `server-side images without local bytes are not kept`() {
        store.persist(
            1L,
            imageMessage(
                "local-user-3", 1L, emptyList(), DeliveryStatus.FAILED,
                remotePaths = listOf("remote"),
            ),
        )

        val kept = store.localAttachmentPathsInUse()
        assertEquals("远程来源没有本机字节可留（空路径已滤掉）", setOf("/tmp/file-local-user-3.txt"), kept)
    }

    @Test
    fun `a row the mapper rejects contributes nothing and never throws`() {
        // 手工塞一条 content 不是 JSON 的行：白名单问不出东西，但不该把选图那条路打成异常
        val log = com.openapi.core.model.ChatLog().apply {
            chatId = "local-broken"
            who = com.openapi.feature.chat.data.mapper.ChatLogMapper.toWho(ChatRole.USER)
            msgType = com.openapi.feature.chat.data.mapper.ChatLogMapper.toMsgType(MessageKind.IMAGE)
            content = "not-json"
            sessionId = 1L
            createTime = java.util.Date(1L)
        }
        db.chatLogDao().save(log)

        assertEquals(emptySet<String>(), store.localAttachmentPathsInUse())
    }
}
