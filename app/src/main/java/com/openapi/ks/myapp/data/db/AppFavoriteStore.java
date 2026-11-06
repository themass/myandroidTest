package com.openapi.ks.myapp.data.db;

import com.openapi.core.event.FavoriteVo;
import com.openapi.core.favorite.FavoriteStore;
import com.openapi.ks.myapp.data.DBManager;

import java.util.List;

/** 用 app.db 实现 [FavoriteStore]，由 MyApplication 注入给 FavoriteUtil。 */
public final class AppFavoriteStore implements FavoriteStore {

    private FavoriteDao dao() {
        return DBManager.getInstance().favoriteDao();
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
