package com.openapi.ks.myapp.data;

import android.content.Context;

import com.openapi.core.util.Background;
import com.openapi.core.util.CollectionUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.ks.myapp.bean.vo.HistoryVo;
import com.openapi.ks.myapp.data.db.AppDatabase;

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
