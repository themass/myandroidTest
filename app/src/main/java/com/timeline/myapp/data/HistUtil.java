package com.timeline.myapp.data;

import android.content.Context;
import android.os.AsyncTask;

import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.ModelUtils;
import com.timeline.myapp.bean.vo.HistVo;
import com.timeline.myapp.data.config.FavoriteChangeEvent;

import java.util.Date;
import java.util.List;

public class HistUtil {
    /**
     * 读取数据库 得到本地收藏
     *
     * @param context
     * @return
     */

    public static List<HistVo> getLocalFavorites(Context context) {
        return DBManager.getInstance().getDaoSession().getHistVoDao().loadAll();
    }

    public static HistVo getFavorite(Context context, String url) {
        List<HistVo> list = DBManager.getInstance().getDaoSession().getHistVoDao().queryRaw("where T.ITEM_URL=?", url);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    public static void getLocalFavoritesAsync(Context context, String url, GetFavoriteListener listener) {
        new GetInfoTask(context, url, listener).execute();
    }

    public static void modLocalFavoritesAsync(Context context, HistVo vo) {
        new ModInfoTask(context, vo).execute();
    }

    /**
     * 添加某个新闻到本地数据库中
     *
     * @param context
     */
    public static void addFavorite(Context context, HistVo vo) {
        vo.setExtra(ModelUtils.entry2Json(vo.getO()));
        vo.setUpdateTime(DateUtils.format(new Date(), DateUtils.DATE_FORMAT));
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
        DBManager.getInstance().getDaoSession().getHistVoDao().deleteAll();
    }

    public interface GetFavoriteListener {
        void isFavorite(String itemUrl, boolean ret);
    }
    public static class AddInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private HistVo vo;

        public AddInfoTask(Context context, HistVo vo) {
            this.context = context;
            this.vo = vo;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            DBManager.getInstance().getDaoSession().getHistVoDao().insertOrReplace(vo);
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
            DBManager.getInstance().getDaoSession().getHistVoDao().deleteByKey(id);
            return Boolean.TRUE;
        }
    }

    public static class GetInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private String itemUrl;
        private GetFavoriteListener listener;

        public GetInfoTask(Context context, String itemUrl, GetFavoriteListener listener) {
            this.context = context;
            this.itemUrl = itemUrl;
            this.listener = listener;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            HistVo vo = getFavorite(context, itemUrl);
            return vo != null ? Boolean.TRUE : Boolean.FALSE;
        }

        @Override
        protected void onPostExecute(Boolean ret) {
            if (listener != null)
                listener.isFavorite(itemUrl, ret);
        }
    }

    public static class ModInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private HistVo vo;

        public ModInfoTask(Context context, HistVo vo) {
            this.context = context;
            this.vo = vo;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            HistVo ret = getFavorite(context, vo.itemUrl);
            if (ret == null) {
                vo.setExtra(ModelUtils.entry2Json(vo.getO()));
                vo.setUpdateTime(DateUtils.format(new Date(), DateUtils.DATE_FORMAT));
                addFavorite(context, vo);
                EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                return true;
            } else {
                EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                delFavorite(context, ret.id);
                return false;
            }
        }
    }
//
//	public static void initFavoriteIds(Context context) {
//		FAVORITE_URLS_SET.clear();
//		if (!AccountModel.isLogged(context)) {
//			List<Map<String, Object>> list = getLocalFavorites(context,
//					new String[] { FAVORITE_URL }, null, null);
//			for (Map<String, Object> map : list) {
//				FAVORITE_URLS_SET.add((String) map.get(FAVORITE_URL));
//			}
//		} else {
//			FavoriteResponse.Urls response = new FavoriteResponse.Urls(
//					context);
//			Gson4MapMultipartRequest request = new Gson4MapMultipartRequest(
//					context, null, Constants.FAVORITE_URL_LIST, AccountModel.getCookieHeader(context),
//					response, response);
//			VolleyUtils.addRequest(request);
//		}
//	}
//
//	public static void syncFavorite(Context context) {
//		List<Map<String, Object>> list = FavoriteModel.getLocalFavorites(
//				context, null, null, null);
//			Map<String, String> param = new HashMap<String, String>();
//			param.put(FavoriteModel.FAVORITE_SYNC_PARAM,
//					ModelUtils.entry2Json(list));
//			FavoriteResponse.Sync response = new FavoriteResponse.Sync(context);
//			Gson4MapMultipartRequest request = new Gson4MapMultipartRequest(
//					context, param, Constants.FAVORITE_SYNC, AccountModel.getCookieHeader(context), response,
//					response);
//			VolleyUtils.addRequest(request);
//	}
}
