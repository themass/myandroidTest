package com.openapi.core.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.openapi.core.event.FavoriteVo;

@Database(entities = {FavoriteVo.class}, version = 1, exportSchema = false)
public abstract class FavoriteDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
}
