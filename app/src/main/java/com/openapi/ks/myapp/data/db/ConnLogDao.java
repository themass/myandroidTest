package com.openapi.ks.myapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.openapi.ks.myapp.bean.form.ConnLog;

import java.util.List;

@Dao
public interface ConnLogDao {

    @Insert
    long insert(ConnLog log);

    @Query("SELECT * FROM ConnLog")
    List<ConnLog> loadAll();

    @Query("DELETE FROM ConnLog")
    void deleteAll();
}
