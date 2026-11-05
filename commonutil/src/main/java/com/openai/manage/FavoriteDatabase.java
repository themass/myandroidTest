package com.openai.manage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.openai.commonevent.FavoriteVo;

@Database(entities = {FavoriteVo.class}, version = 1, exportSchema = false)
public abstract class FavoriteDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
}
