package com.openapi.ks.myapp.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.ks.myapp.bean.vo.HistoryVo;

import java.util.List;

@Dao
public interface HistoryDao {

    @Query("SELECT * FROM HistoryVo WHERE itemUrl = :url")
    List<HistoryVo> byUrl(String url);

    /** itemUrl 唯一；已存在就忽略，等价于旧代码里"插入失败就吞掉异常"。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(HistoryVo vo);
}
