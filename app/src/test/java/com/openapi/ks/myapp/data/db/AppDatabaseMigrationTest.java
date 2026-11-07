package com.openapi.ks.myapp.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.event.FavoriteVo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * 版本 1 → 2 的迁移：用 Room 为 v1 生成的建表语句造一个真实的旧库文件，
 * 放入一条聊天记录，再用 MIGRATION_1_2 打开。聊天数据要保留，收藏表要可用。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class AppDatabaseMigrationTest {

    private static final String NAME = "migration-1-2-test.db";
    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(NAME);
        createVersion1Database();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        context.deleteDatabase(NAME);
    }

    /** v1 的表结构，与 Room 为版本 1 生成的 createAllTables 完全一致（没有收藏表）。 */
    private void createVersion1Database() {
        File file = context.getDatabasePath(NAME);
        file.getParentFile().mkdirs();
        SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            raw.execSQL("CREATE TABLE IF NOT EXISTS `ChatLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `content` TEXT, `who` INTEGER NOT NULL, `msgType` INTEGER NOT NULL, `chatId` TEXT, `createTime` INTEGER, `sessionId` INTEGER)");
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ChatLog_chatId` ON `ChatLog` (`chatId`)");
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_ChatLog_sessionId` ON `ChatLog` (`sessionId`)");
            raw.execSQL("CREATE TABLE IF NOT EXISTS `ChatSessionLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `createTime` INTEGER, `setting` TEXT, `type` INTEGER NOT NULL)");
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ChatSessionLog_name` ON `ChatSessionLog` (`name`)");
            raw.execSQL("CREATE TABLE IF NOT EXISTS `ConnLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `host` TEXT, `userIp` TEXT, `status` INTEGER NOT NULL, `time` TEXT)");
            raw.execSQL("CREATE TABLE IF NOT EXISTS `HistoryVo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `itemUrl` TEXT)");
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_HistoryVo_itemUrl` ON `HistoryVo` (`itemUrl`)");
            raw.execSQL("INSERT INTO ChatLog (id, content, who, msgType, chatId) VALUES (1, 'kept', 0, 0, 'c-1')");
            raw.execSQL("PRAGMA user_version = 1");
        } finally {
            raw.close();
        }
    }

    @Test
    public void upgradeKeepsChatDataAndAddsFavorites() {
        db = Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();

        try (Cursor c = db.query(new SimpleSQLiteQuery("SELECT content FROM ChatLog WHERE id = 1"))) {
            assertTrue(c.moveToFirst());
            assertEquals("kept", c.getString(0));
        }

        FavoriteVo vo = new FavoriteVo();
        vo.itemUrl = "after-upgrade";
        vo.type = 1;
        vo.name = "n";
        db.favoriteDao().insertOrReplace(vo);
        assertEquals(1, db.favoriteDao().byUrl("after-upgrade").size());
        assertEquals(1, db.favoriteDao().localFavorites().size());
    }
}
