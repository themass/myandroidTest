package com.openapi.feature.chat.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openapi.core.db.AppDatabase
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.VoiceAttachment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * P0-6.3：cacheRemote 改成「整页一个事务 + 一次批量写」后，合并语义必须与逐条 persist 的旧写法完全一致。
 *
 * 这里先钉住旧行为（特征测试），再换实现：临时 id 行改挂服务端 id、本机图片文件保留、
 * 整页刷新只清服务端能重建的纯文字行。任何一条变了就是改了行为，不是性能优化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalChatStoreCacheRemoteTest {

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
    fun close() {
        db.close()
    }

    private fun msg(
        id: String,
        role: ChatRole = ChatRole.ASSISTANT,
        text: String = "hi",
        kind: MessageKind = MessageKind.TEXT,
        images: List<ImageAttachment> = emptyList(),
        voice: VoiceAttachment? = null,
        clientMessageId: String? = null,
        delivery: DeliveryStatus = DeliveryStatus.SENT,
        at: Long = 1_700_000_000_000L,
    ) = ChatMessage(
        id = id,
        role = role,
        text = text,
        createdAtMillis = at,
        kind = kind,
        voice = voice,
        images = images,
        clientMessageId = clientMessageId,
        delivery = delivery,
    )

    @Test
    fun cacheRemoteInsertsServerMessages() {
        store.cacheRemote(
            1L,
            listOf(
                msg("s1", text = "第一句"),
                msg("s2", role = ChatRole.USER, text = "第二句"),
            ),
            replaceAll = false,
        )
        val cached = store.loadCached(1L)
        assertEquals(listOf("s1", "s2"), cached.map { it.id })
        assertEquals("第一句", cached[0].text)
        assertEquals("第二句", cached[1].text)
    }

    @Test
    fun cacheRemoteRehostsLocalRowToServerId() {
        store.persist(1L, msg("local-1", role = ChatRole.USER, text = "你好", delivery = DeliveryStatus.SENDING))
        store.cacheRemote(
            1L,
            listOf(msg("srv-9", role = ChatRole.USER, text = "你好呀", clientMessageId = "local-1")),
            replaceAll = false,
        )
        val cached = store.loadCached(1L)
        assertEquals(1, cached.size)
        val merged = cached[0]
        assertEquals("srv-9", merged.id)
        assertEquals("你好呀", merged.text)
        assertEquals(DeliveryStatus.SENT, merged.delivery)
        // 旧临时 id 行必须删掉，不能留下「同一句话两行」
        assertEquals(1, db.chatLogDao().bySession(1L).size)
    }

    @Test
    fun cacheRemoteKeepsLocalImageFileAndMergesUploadId() {
        val file = File.createTempFile("chat-img", ".jpg")
        file.deleteOnExit()
        store.persist(
            1L,
            msg(
                "local-2", role = ChatRole.USER, text = "看图", kind = MessageKind.IMAGE,
                images = listOf(ImageAttachment(file.path)),
                delivery = DeliveryStatus.SENDING,
            ),
        )
        store.cacheRemote(
            1L,
            listOf(
                msg(
                    "srv-10", role = ChatRole.USER, text = "看图", kind = MessageKind.IMAGE,
                    images = listOf(ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = "u1")),
                    clientMessageId = "local-2",
                ),
            ),
            replaceAll = false,
        )
        val cached = store.loadCached(1L)
        assertEquals(1, cached.size)
        val image = cached[0].images.single()
        // 本机文件还在就继续用本机文件，只把服务端的 upload_id 合进来
        assertEquals(ImageSource.FILE, image.source)
        assertEquals(file.path, image.path)
        assertEquals("u1", image.uploadId)
        assertEquals(1, db.chatLogDao().bySession(1L).size)
    }

    @Test
    fun replaceAllRefreshDropsServerTextRowsAndKeepsRichRows() {
        store.persist(
            1L,
            msg("v1", kind = MessageKind.VOICE, text = "转写", voice = VoiceAttachment("/tmp/v.mp3", 3)),
        )
        store.persist(1L, msg("t1", text = "旧文字"))
        store.cacheRemote(1L, listOf(msg("s20", text = "新文字")), replaceAll = true)
        val cached = store.loadCached(1L)
        assertEquals(setOf("v1", "s20"), cached.map { it.id }.toSet())
        assertEquals(MessageKind.VOICE, cached.first { it.id == "v1" }.kind)
    }
}
