package com.openapi.core.db;

import com.openapi.core.db.FavoriteVo;
import com.openapi.core.favorite.FavoriteStore;

import java.util.List;

/** 用 app.db 实现 [FavoriteStore]，由 MyApplication 注入给 FavoriteUtil。 */
public final class AppFavoriteStore implements FavoriteStore {

    private FavoriteDao dao() {
        return AppDatabase.getInstance().favoriteDao();
    }

    @Override
    public List<FavoriteVo> localFavorites() {
        return dao().localFavorites();
    }

    @Override
    public List<FavoriteVo> byUrl(String url) {
        return dao().byUrl(url);
    }

    @Override
    public long insertOrReplace(FavoriteVo vo) {
        return dao().insertOrReplace(vo);
    }

    @Override
    public void deleteById(long id) {
        dao().deleteById(id);
    }

    @Override
    public void deleteAll() {
        dao().deleteAll();
    }
}
