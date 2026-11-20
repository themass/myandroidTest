package com.openapi.core.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.model.ChatLog;
import com.openapi.core.model.ChatMessageHit;
import com.openapi.core.model.ChatSessionLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * 整改清单 P2-15：消息搜索。数据只有本地缓存这一份权威副本（服务端没有搜索接口），
 * 所以搜索落在 Room 查询上，并用测试钉住三件事：
 * LIKE 元字符按字面量处理、命中消息能带出所属会话名和预览、非文字行不参与搜索。
 * 会话名搜索不在这层——会话列表整表已在内存里（ChatUiState.sessions），
 * 在 presentation 层过滤（见 ChatSearchTest），DAO 查询是死代码。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class ChatSearchDaoTest {
    private AppDatabase db;

    @Before
    public void open() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries() // 测试库允许主线程；生产路径由调用方切 IO
                .build();
    }

    @After
    public void close() {
        db.close();
    }

    private long session(long id, String name) {
        ChatSessionLog log = new ChatSessionLog();
        log.id = id;
        log.name = name;
        db.chatSessionLogDao().insertOrReplace(log);
        return id;
    }

    private void message(long sessionId, String chatId, String content, int msgType) {
        ChatLog log = new ChatLog();
        log.sessionId = sessionId;
        log.chatId = chatId;
        log.content = content;
        log.msgType = msgType;
        db.chatLogDao().save(log);
    }

    @Test
    public void messageSearchReturnsSessionAndPreview() {
        session(1L, "会话一");
        session(2L, "会话二");
        message(1L, "a1", "周五要去医院复查", 0);
        message(2L, "b1", "周五的比赛别忘了", 0);
        message(2L, "b2", "和它无关的话", 0);

        List<ChatMessageHit> hits = db.chatLogDao().searchContent("医院", 20);
        assertEquals(1, hits.size());
        assertEquals(Long.valueOf(1L), hits.get(0).sessionId);
        assertEquals("会话一", hits.get(0).sessionName);
        assertTrue(hits.get(0).preview.contains("医院"));
    }

    @Test
    public void messageSearchTreatsLikeMetacharactersLiterally() {
        session(1L, "会话一");
        message(1L, "a1", "准确率达到 100% 了", 0);
        message(1L, "a2", "准确率达到 100x 了", 0);

        // 「%」在 LIKE 里是通配符；不转义的话查 "100%" 会把 "100x" 也捞出来
        List<ChatMessageHit> hits = db.chatLogDao().searchContent("100%", 20);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).preview.contains("100%"));
    }

    @Test
    public void messageSearchSkipsNonTextRows() {
        session(1L, "会话一");
        message(1L, "a1", "/sdcard/voice/医院.m4a", 1); // 语音行的 content 是本机路径，不参与搜索

        assertTrue(db.chatLogDao().searchContent("医院", 20).isEmpty());
    }

    @Test
    public void messageSearchHonoursLimit() {
        session(1L, "会话一");
        for (int i = 0; i < 5; i++) message(1L, "m" + i, "重复关键词 " + i, 0);

        assertEquals(3, db.chatLogDao().searchContent("重复关键词", 3).size());
    }

    @Test
    public void blankQuerySearchesNothing() {
        session(1L, "会话一");
        message(1L, "m1", "你好", 0);

        assertTrue(db.chatLogDao().searchContent("", 20).isEmpty());
        assertTrue(db.chatLogDao().searchContent("   ", 20).isEmpty());
    }
}
