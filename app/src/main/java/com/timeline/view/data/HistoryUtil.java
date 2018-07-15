package com.timeline.view.data;

import android.content.Context;
import android.os.AsyncTask;

import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.timeline.view.bean.vo.HistoryVo;

import java.util.List;

public class HistoryUtil {
    public static HistoryVo getHistory(Context context, String url) {
        List<HistoryVo> list = DBManager.getInstance().getDaoSession().getHistoryVoDao().queryRaw("where T.ITEM_URL=?", url);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    public static void addHistory(Context context, String url) {
        new AddReadedInfoTask(context, url).execute();
    }

    public static class AddReadedInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private String url;

        public AddReadedInfoTask(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (HistoryUtil.getHistory(context, url) == null) {
                HistoryVo vo = new HistoryVo();
                vo.setItemUrl(url);
                try {
                    DBManager.getInstance().getDaoSession().getHistoryVoDao().insert(vo);
                } catch (Throwable e) {
                    LogUtil.e(e);
                }
            }
            return Boolean.TRUE;
        }
    }

}
