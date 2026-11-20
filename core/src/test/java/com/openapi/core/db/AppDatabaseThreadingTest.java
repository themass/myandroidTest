package com.openapi.core.db;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 整改清单 P3-21：生产库不许再 {@code allowMainThreadQueries()}。
 *
 * Room 在没有该标志时，从主线程发起查询会抛 IllegalStateException
 * （"Cannot access database on the main thread"）——这正是旧代码里
 * 「主线程直接读写 app.db」一直没人发现的原因：崩溃被标志吞掉了，
 * 代价是主线程卡顿。这个测试把「主线程查询会被拒绝」钉成契约：
 * 谁再把标志加回来，这里立刻红。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class AppDatabaseThreadingTest {

    @Before
    public void reset() {
        AppDatabase.closeConnection();
    }

    @After
    public void close() {
        AppDatabase.closeConnection();
    }

    /** 生产入口建出来的库：主线程查询必须被 Room 拒绝。 */
    @Test
    public void mainThreadQueryIsRejected() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.init(context);
        assertThrows("主线程还能直接查库，说明 allowMainThreadQueries 被加回来了",
                IllegalStateException.class,
                () -> AppDatabase.getInstance().chatLogDao().count());
    }

    /**
     * 启动路径：{@code init()} 在 Application.onCreate（主线程）调用，
     * 它自己绝不能碰数据库——聊天记录条数那条日志要在后台打。
     * 标志去掉后如果 init 还同步查库，这里会以 IllegalStateException 失败。
     */
    @Test
    public void initFromMainThreadDoesNotTouchTheDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase.init(context); // 不许抛 Cannot access database on the main thread
    }
}
