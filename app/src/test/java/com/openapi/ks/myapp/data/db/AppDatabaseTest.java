package com.openapi.ks.myapp.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.model.ChatLog;
import com.openapi.core.model.ChatSessionLog;
import com.openapi.core.model.ConnLog;
import com.openapi.core.model.HistoryVo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 用内存里的真实 SQLite 验证 greenDAO → Room 迁移后各 DAO 的行为。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class AppDatabaseTest {
    private AppDatabase db;

    @Before
    public void open() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void close() {
        db.close();
    }

    private ChatLog log(String chatId, long sessionId) {
        ChatLog log = new ChatLog();
        log.chatId = chatId;
        log.sessionId = sessionId;
        log.content = "c-" + chatId;
        log.createTime = new Date(1_700_000_000_000L);
        return log;
    }

    @Test
    public void savedLogRoundTripsWithDateAndDefaults() {
        db.chatLogDao().save(log("a", 7));
        ChatLog read = db.chatLogDao().bySession(7).get(0);
        assertEquals("a", read.chatId);
        assertEquals(new Date(1_700_000_000_000L), read.createTime);
        assertNotNull(read.id);
        assertEquals(chat.ui.data.model.MessageType.TXT_TYPE, read.msgType);
    }

    @Test
    public void bySessionFiltersAndKeepsInsertionOrder() {
        db.chatLogDao().save(log("a", 1));
        db.chatLogDao().save(log("b", 2));
        db.chatLogDao().save(log("c", 1));
        List<ChatLog> session1 = db.chatLogDao().bySession(1);
        assertEquals(2, session1.size());
        assertEquals("a", session1.get(0).chatId);
        assertEquals("c", session1.get(1).chatId);
        assertTrue(db.chatLogDao().bySession(99).isEmpty());
    }

    @Test
    public void savingSameChatIdTwiceKeepsOneRowInsteadOfThrowing() {
        ChatLog first = log("dup", 1);
        first.content = "old";
        db.chatLogDao().save(first);
        ChatLog second = log("dup", 1);
        second.content = "new";
        db.chatLogDao().save(second);
        List<ChatLog> all = db.chatLogDao().bySession(1);
        assertEquals(1, all.size());
        assertEquals("new", all.get(0).content);
    }

    @Test
    public void deleteByChatIdRemovesOnlyThatMessage() {
        db.chatLogDao().save(log("keep", 1));
        db.chatLogDao().save(log("drop", 1));
        db.chatLogDao().deleteByChatId("drop");
        List<ChatLog> left = db.chatLogDao().bySession(1);
        assertEquals(1, left.size());
        assertEquals("keep", left.get(0).chatId);
    }

    @Test
    public void trimKeepsOnlyTheLatestThirty() {
        for (int i = 1; i <= 40; i++) {
            db.chatLogDao().save(log("m" + i, 1));
        }
        db.chatLogDao().trimToLatest(30);
        List<ChatLog> left = db.chatLogDao().bySession(1);
        assertEquals(30, left.size());
        assertEquals("m11", left.get(0).chatId);
        assertEquals("m40", left.get(29).chatId);
    }

    @Test
    public void trimDoesNothingWhenThereAreFewerThanThirty() {
        for (int i = 1; i <= 5; i++) {
            db.chatLogDao().save(log("m" + i, 1));
        }
        db.chatLogDao().trimToLatest(30);
        assertEquals(5, db.chatLogDao().bySession(1).size());
        db.chatLogDao().deleteAll();
        db.chatLogDao().trimToLatest(30); // 空表上不能抛
        assertEquals(0, db.chatLogDao().count());
    }

    /** 本地没有默认会话（会话列表以服务端为准）：新会话拿到正数 id，并能按 id 读回。 */
    @Test
    public void newSessionsGetPositiveIdsAndLoadBack() {
        ChatSessionLog custom = new ChatSessionLog();
        custom.name = "mine";
        custom.setting = "";
        long id = db.chatSessionLogDao().insertOrReplace(custom);
        assertTrue("new sessions must get a positive id", id > 0);
        assertEquals("mine", db.chatSessionLogDao().load(id).name);
        assertEquals(1, db.chatSessionLogDao().loadAll().size());
    }

    @Test
    public void serverSessionsKeepTheirOwnIdsAndReplaceOnReinsert() {
        ChatSessionLog a = new ChatSessionLog();
        a.id = 1000L;
        a.name = "translation";
        a.setting = "t";
        ChatSessionLog b = new ChatSessionLog();
        b.id = 1001L;
        b.name = "story";
        b.setting = "s";
        List<ChatSessionLog> fromServer = new ArrayList<>();
        fromServer.add(a);
        fromServer.add(b);
        db.chatSessionLogDao().insertOrReplaceAll(fromServer);
        db.chatSessionLogDao().insertOrReplaceAll(fromServer); // 每次启动都会拉一遍
        assertEquals(2, db.chatSessionLogDao().loadAll().size());
        assertEquals("translation", db.chatSessionLogDao().load(1000).name);
    }

    @Test
    public void deleteSessionById() {
        ChatSessionLog s = new ChatSessionLog();
        s.name = "gone";
        s.setting = "";
        long id = db.chatSessionLogDao().insertOrReplace(s);
        db.chatSessionLogDao().deleteById(id);
        assertNull(db.chatSessionLogDao().load(id));
    }

    @Test
    public void historyIgnoresDuplicateUrls() {
        HistoryVo vo = new HistoryVo();
        vo.itemUrl = "http://x/1";
        db.historyDao().insert(vo);
        HistoryVo again = new HistoryVo();
        again.itemUrl = "http://x/1";
        db.historyDao().insert(again); // 不能抛
        assertEquals(1, db.historyDao().byUrl("http://x/1").size());
        assertTrue(db.historyDao().byUrl("http://x/2").isEmpty());
    }

    @Test
    public void connLogsAreReadThenCleared() {
        ConnLog c = new ConnLog();
        c.name = "n";
        c.host = "h";
        c.status = 1;
        db.connLogDao().insert(c);
        assertEquals(1, db.connLogDao().loadAll().size());
        db.connLogDao().deleteAll();
        assertTrue(db.connLogDao().loadAll().isEmpty());
    }
}
