package com.openapi.core.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.model.HistoryVo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/** 整改清单 P3-21：列表页着色需要「全部访问过的地址」一次性取回，不再逐条查。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class HistoryDaoAllUrlsTest {
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

    @Test
    public void allUrlsReturnsEveryVisitedUrlOnce() {
        HistoryVo a = new HistoryVo();
        a.setItemUrl("http://x/1");
        HistoryVo again = new HistoryVo();
        again.setItemUrl("http://x/1");
        HistoryVo b = new HistoryVo();
        b.setItemUrl("http://x/2");
        db.historyDao().insert(a);
        db.historyDao().insert(again);
        db.historyDao().insert(b);

        List<String> urls = db.historyDao().allUrls();
        assertEquals(2, urls.size());
        assertTrue(urls.contains("http://x/1"));
        assertTrue(urls.contains("http://x/2"));
    }

    @Test
    public void allUrlsIsEmptyWhenNothingVisited() {
        assertTrue(db.historyDao().allUrls().isEmpty());
    }
}
