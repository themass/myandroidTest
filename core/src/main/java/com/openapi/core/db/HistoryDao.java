package com.openapi.core.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.openapi.core.model.HistoryVo;

import java.util.List;

@Dao
public interface HistoryDao {

    @Query("SELECT * FROM HistoryVo WHERE itemUrl = :url")
    List<HistoryVo> byUrl(String url);

    /** 整改清单 P3-21：列表页一次性取回全部访问过的地址，不再在每个 item 绑定时逐条查。 */
    @Query("SELECT itemUrl FROM HistoryVo")
    List<String> allUrls();

    /** itemUrl 唯一；已存在就忽略，等价于旧代码里"插入失败就吞掉异常"。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(HistoryVo vo);
}
