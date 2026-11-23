package com.openapi.core.favorite;

import com.openapi.core.db.FavoriteVo;

import java.util.List;

/**
 * 收藏的存储接口。框架层（commonutil）只依赖这个接口，实现由 app 注入，
 * 实际数据放在 app 的 Room 数据库 app.db 里。
 */
public interface FavoriteStore {

    /** 本地收藏（只含文字、声音、图片、视频几类）。 */
    List<FavoriteVo> localFavorites();

    List<FavoriteVo> byUrl(String url);

    /** 同一个 itemUrl 再次收藏时覆盖旧记录。 */
    long insertOrReplace(FavoriteVo vo);

    void deleteById(long id);

    void deleteAll();
}
