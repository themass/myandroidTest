package com.openapi.ks.myapp.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.openai.commonevent.FavoriteVo;
import com.openai.manage.FavoriteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** 收藏库（commonutil 里的 Room 库）的行为。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class FavoriteDatabaseTest {
    private FavoriteDatabase db;

    @Before
    public void open() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, FavoriteDatabase.class)
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
        assertEquals(null, db.favoriteDao().byUrl("o").get(0).o);
    }
}
