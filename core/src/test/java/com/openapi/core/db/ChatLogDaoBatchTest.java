package com.openapi.core.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.model.ChatLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/** P0-6.3：批量 upsert / 批量删除的 DAO 行为（cacheRemote 整页写入靠这两条省掉逐条事务）。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class ChatLogDaoBatchTest {

    private AppDatabase db;
    private ChatLogDao dao;

    @Before
    public void open() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.chatLogDao();
    }

    @After
    public void close() {
        db.close();
    }

    private ChatLog log(String chatId) {
        ChatLog log = new ChatLog();
        log.chatId = chatId;
        log.sessionId = 1L;
        log.content = "c-" + chatId;
        log.createTime = new Date(1_700_000_000_000L);
        return log;
    }

    @Test
    public void saveAllInsertsAndReplacesWithoutDuplicates() {
        dao.saveAll(Arrays.asList(log("a"), log("b"), log("c")));
        assertEquals(3, dao.count());

        ChatLog replaced = dao.byChatId("b");
        replaced.content = "replaced";
        dao.saveAll(Collections.singletonList(replaced));

        assertEquals(3, dao.count());
        assertEquals("replaced", dao.byChatId("b").content);
    }

    @Test
    public void deleteByChatIdsRemovesOnlyGivenRows() {
        dao.saveAll(Arrays.asList(log("a"), log("b"), log("c")));
        dao.deleteByChatIds(Arrays.asList("a", "c"));
        assertEquals(1, dao.count());
        assertNotNull(dao.byChatId("b"));
        assertNull(dao.byChatId("a"));
    }
}
