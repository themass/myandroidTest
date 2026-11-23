package com.openapi.feature.content.common;

import android.content.Context;

import com.openapi.common.util.android.Background;
import com.openapi.common.util.lang.CollectionUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.core.model.HistoryVo;
import com.openapi.core.db.AppDatabase;

import java.util.List;

public class HistoryUtil {

    /** 已访问地址列表的回调，一定在主线程触发。 */
    public interface VisitedUrlsListener {
        void onVisited(java.util.List<String> urls);
    }

    /**
     * 整改清单 P3-21：列表页一次性取回全部访问过的地址（后台读、主线程回调）。
     * 读失败按空列表交付——着色只是辅助信息，不该因为读库失败卡住列表。
     */
    public static void loadVisitedUrls(Context context, VisitedUrlsListener listener) {
        Background.parallel(() -> {
            java.util.List<String> urls;
            try {
                urls = AppDatabase.getInstance().historyDao().allUrls();
            } catch (Throwable e) {
                LogUtil.e(e);
                urls = java.util.Collections.emptyList();
            }
            final java.util.List<String> result = urls;
            Background.main(() -> listener.onVisited(result));
        });
    }

    /**
     * 单条查询。只许在后台线程调用（AppDatabase 不再允许主线程查询）；
     * UI 侧一律走 {@link #loadVisitedUrls} + {@link VisitedUrlCache}。
     */
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
