package com.openapi.core.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.db.FavoriteVo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 整改清单 P3-21：{@code allowMainThreadQueries()} 关掉之后，UI 再同步读收藏表就会直接崩。
 * 异步入口必须保证两件事：读表发生在后台线程，回调回到主线程。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class FavoriteUtilAsyncTest {

    /** 记录每个方法被哪个线程调用的假存储。 */
    private static class RecordingStore implements FavoriteStore {
        final AtomicReference<Thread> localFavoritesThread = new AtomicReference<>();
        final AtomicReference<Thread> byUrlThread = new AtomicReference<>();
        final List<FavoriteVo> favorites = new ArrayList<>();
        FavoriteVo byUrl;

        @Override
        public List<FavoriteVo> localFavorites() {
            localFavoritesThread.set(Thread.currentThread());
            return favorites;
        }

        @Override
        public List<FavoriteVo> byUrl(String url) {
            byUrlThread.set(Thread.currentThread());
            return byUrl == null ? new ArrayList<>() : Arrays.asList(byUrl);
        }

        @Override
        public long insertOrReplace(FavoriteVo vo) { return 0; }

        @Override
        public void deleteById(long id) { }

        @Override
        public void deleteAll() { }
    }

    private RecordingStore store;
    private Context context;

    @Before
    public void install() {
        store = new RecordingStore();
        FavoriteUtil.install(store);
        context = ApplicationProvider.getApplicationContext();
    }

    /** Robolectric 把主线程命名为 "SDK 34 Main Thread"，比对线程对象而不是名字。 */
    private void assertOnMain(String who, Thread t) {
        org.junit.Assert.assertSame(who + "必须跑在主线程线程上",
                Looper.getMainLooper().getThread(), t);
    }

    /** Robolectric 默认暂停主线程 Looper：轮询把后台线程投回主线程的回调放出来。 */
    private void drainMainLooper(AtomicBoolean done) throws InterruptedException {
        for (int i = 0; i < 200 && !done.get(); i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue("回调没有回到主线程", done.get());
    }

    @Test
    public void getLocalFavoritesAsyncReadsOffTheMainThreadAndDeliversOnMain() throws Exception {
        FavoriteVo vo = new FavoriteVo();
        vo.itemUrl = "http://a/b";
        store.favorites.add(vo);

        AtomicReference<List<FavoriteVo>> got = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();

        FavoriteUtil.getLocalFavoritesAsync(context, list -> {
            callbackThread.set(Thread.currentThread());
            got.set(list);
            done.set(true);
        });
        drainMainLooper(done);

        assertNotNull(got.get());
        assertEquals(1, got.get().size());
        assertEquals("http://a/b", got.get().get(0).itemUrl);
        assertOnMain("回调", callbackThread.get());
        assertNotNull("存储层没被调用", store.localFavoritesThread.get());
        org.junit.Assert.assertNotSame("读表不许发生在主线程",
                Looper.getMainLooper().getThread(), store.localFavoritesThread.get());
    }

    @Test
    public void getFavoriteAsyncReadsOffTheMainThreadAndDeliversOnMain() throws Exception {
        FavoriteVo vo = new FavoriteVo();
        vo.itemUrl = "http://c/d";
        store.byUrl = vo;

        AtomicReference<FavoriteVo> got = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();

        FavoriteUtil.getFavoriteAsync(context, "http://c/d", result -> {
            callbackThread.set(Thread.currentThread());
            got.set(result);
            done.set(true);
        });
        drainMainLooper(done);

        assertNotNull(got.get());
        assertEquals("http://c/d", got.get().itemUrl);
        assertOnMain("回调", callbackThread.get());
        assertNotNull("存储层没被调用", store.byUrlThread.get());
        org.junit.Assert.assertNotSame("读表不许发生在主线程",
                Looper.getMainLooper().getThread(), store.byUrlThread.get());
    }

    /** 没命中时回调也必须回来（null），否则长按菜单永远弹不出来。 */
    @Test
    public void getFavoriteAsyncDeliversNullWhenNothingMatches() throws Exception {
        AtomicInteger called = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();
        FavoriteUtil.getFavoriteAsync(context, "http://none", result -> {
            if (result == null) called.incrementAndGet();
            done.set(true);
        });
        drainMainLooper(done);
        assertEquals(1, called.get());
    }
}
