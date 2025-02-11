package com.openai.commonutil;

import android.content.Context;
import android.os.AsyncTask;

import com.openai.commonevent.FavoriteChangeEvent;
import com.openai.commonevent.FavoriteVo;
import com.openai.commonutil.CollectionUtils;
import com.openai.commonutil.DateUtils;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.ModelUtils;
import com.openai.manage.DBManager;

import java.util.Date;
import java.util.List;

public class FavoriteUtil {
    /**
     * 读取数据库 得到本地收藏
     *
     * @param context
     * @return
     */

    public static List<FavoriteVo> getLocalFavorites(Context context) {
        return DBManager.getInstance().getDaoSession().getFavoriteVoDao().queryRaw("where T.TYPE < 4");
    }

    public static FavoriteVo getFavorite(Context context, String url) {
        try {
            List<FavoriteVo> list = DBManager.getInstance().getDaoSession().getFavoriteVoDao().queryRaw("where T.ITEM_URL=?", url);
            if (CollectionUtils.isEmpty(list)) {
                return null;
            }
            return list.get(0);
        }catch (Exception e){
            return null;
        }
    }

    public static void getLocalFavoritesAsync(Context context, String url, GetFavoriteListener listener) {
        new GetInfoTask(context, url, listener).execute();
    }

    public static void modLocalFavoritesAsync(Context context, FavoriteVo vo, ModFavoriteListener listener) {
        new ModInfoTask(context, vo, listener).execute();
    }

    /**
     * 添加某个新闻到本地数据库中
     *
     * @param context
     */
    public static void addFavorite(Context context, FavoriteVo vo) {
        new AddInfoTask(context, vo).execute();
    }

    /**
     * 删除收藏库
     *
     * @param context
     */
    public static void delFavorite(Context context, Long id) {
        new DelInfoTask(context, id).execute();
    }

    /**
     * 清空本地收藏数据库
     *
     * @param context
     */
    public static void delAllLocalFavorite(Context context) {
        DBManager.getInstance().getDaoSession().getFavoriteVoDao().deleteAll();
    }

    public interface GetFavoriteListener {
        void isFavorite(String itemUrl, FavoriteVo vo);
    }

    public interface ModFavoriteListener {
        void modFavorite(FavoriteVo vo);
    }

    public static class AddInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private FavoriteVo vo;

        public AddInfoTask(Context context, FavoriteVo vo) {
            this.context = context;
            this.vo = vo;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            DBManager.getInstance().getDaoSession().getFavoriteVoDao().insertOrReplace(vo);
            return Boolean.TRUE;
        }
    }

    public static class DelInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private long id;

        public DelInfoTask(Context context, long id) {
            this.context = context;
            this.id = id;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            DBManager.getInstance().getDaoSession().getFavoriteVoDao().deleteByKey(id);
            return Boolean.TRUE;
        }
    }

    public static class GetInfoTask extends AsyncTask<String, Integer, FavoriteVo> {
        private Context context;
        private String itemUrl;
        private GetFavoriteListener listener;

        public GetInfoTask(Context context, String itemUrl, GetFavoriteListener listener) {
            this.context = context;
            this.itemUrl = itemUrl;
            this.listener = listener;
        }

        @Override
        protected FavoriteVo doInBackground(String... params) {
            return getFavorite(context, itemUrl);
        }

        @Override
        protected void onPostExecute(FavoriteVo vo) {
            if (listener != null)
                listener.isFavorite(itemUrl, vo);
        }
    }

    public static class ModInfoTask extends AsyncTask<String, Integer, FavoriteVo> {
        private Context context;
        private FavoriteVo vo;
        private ModFavoriteListener listener;

        public ModInfoTask(Context context, FavoriteVo vo, ModFavoriteListener listener) {
            this.context = context;
            this.vo = vo;
            this.listener = listener;
        }

        @Override
        protected FavoriteVo doInBackground(String... params) {
            FavoriteVo ret = getFavorite(context, vo.itemUrl);
            if (ret == null) {
                vo.setExtra(ModelUtils.entry2Json(vo.getO()));
                vo.setUpdateTime(DateUtils.format(new Date(), DateUtils.DATE_FORMAT));
                addFavorite(context, vo);
                EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                return vo;
            } else {
                EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                delFavorite(context, ret.id);
                return null;
            }
        }

        @Override
        protected void onPostExecute(FavoriteVo vo) {
            if (listener != null)
                listener.modFavorite(vo);
        }
    }
}
