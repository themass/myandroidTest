package com.openapi.ks.myapp.data.db;

import com.openapi.core.db.AppDatabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openapi.core.event.FavoriteVo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** 收藏表（现在在 app.db 里）的 DAO 行为。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class FavoriteDaoTest {
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

    private FavoriteVo fav(String url, int type) {
        FavoriteVo vo = new FavoriteVo();
        vo.itemUrl = url;
        vo.type = type;
        vo.name = "n-" + url;
        vo.extra = "{}";
        return vo;
    }

    @Test
    public void localFavoritesExcludeTypeFourAndAbove() {
        db.favoriteDao().insertOrReplace(fav("a", 0));
        db.favoriteDao().insertOrReplace(fav("b", 3));
        db.favoriteDao().insertOrReplace(fav("c", 4));
        assertEquals(2, db.favoriteDao().localFavorites().size());
    }

    @Test
    public void favoritingTheSameUrlAgainReplacesTheOldRow() {
        FavoriteVo first = fav("same", 1);
        first.name = "old";
        db.favoriteDao().insertOrReplace(first);
        FavoriteVo second = fav("same", 1);
        second.name = "new";
        db.favoriteDao().insertOrReplace(second);
        assertEquals(1, db.favoriteDao().byUrl("same").size());
        assertEquals("new", db.favoriteDao().byUrl("same").get(0).name);
    }

    @Test
    public void deleteByIdAndDeleteAll() {
        db.favoriteDao().insertOrReplace(fav("a", 0));
        db.favoriteDao().insertOrReplace(fav("b", 0));
        long id = db.favoriteDao().byUrl("a").get(0).id;
        db.favoriteDao().deleteById(id);
        assertTrue(db.favoriteDao().byUrl("a").isEmpty());
        assertEquals(1, db.favoriteDao().localFavorites().size());
        db.favoriteDao().deleteAll();
        assertTrue(db.favoriteDao().localFavorites().isEmpty());
    }

    @Test
    public void runtimeObjectIsNotPersisted() {
        FavoriteVo vo = fav("o", 0);
        vo.o = new Object();
        db.favoriteDao().insertOrReplace(vo);
        assertNull(db.favoriteDao().byUrl("o").get(0).o);
    }
}
