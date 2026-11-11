package com.openapi.feature.content.common;

import android.content.Context;

import com.openapi.common.util.android.Background;
import com.openapi.common.util.lang.CollectionUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.core.model.HistoryVo;
import com.openapi.core.db.AppDatabase;

import java.util.List;

public class HistoryUtil {
    public static HistoryVo getHistory(Context context, String url) {
        List<HistoryVo> list = AppDatabase.getInstance().historyDao().byUrl(url);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /** 在后台记录一条浏览历史；同一个地址只记一次。 */
    public static void addHistory(Context context, String url) {
        Background.serial(() -> {
            if (HistoryUtil.getHistory(context, url) == null) {
                HistoryVo vo = new HistoryVo();
                vo.setItemUrl(url);
                try {
                    AppDatabase.getInstance().historyDao().insert(vo);
                } catch (Throwable e) {
                    LogUtil.e(e);
                }
            }
        });
    }

}
