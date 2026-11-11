package com.openapi.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.core.event.FavoriteVo;

import java.util.List;

@Dao
public interface FavoriteDao {

    /** 本地收藏只保留 type < 4 的几类（文字、声音、图片、视频）。 */
    @Query("SELECT * FROM FavoriteVo WHERE type < 4")
    List<FavoriteVo> localFavorites();

    @Query("SELECT * FROM FavoriteVo WHERE itemUrl = :url")
    List<FavoriteVo> byUrl(String url);

    /** 同一个 itemUrl 再次收藏时覆盖旧记录。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(FavoriteVo vo);

    @Query("DELETE FROM FavoriteVo WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM FavoriteVo")
    void deleteAll();
}
