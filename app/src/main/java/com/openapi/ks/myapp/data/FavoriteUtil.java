package com.openapi.ks.myapp.data;

import android.content.Context;
import android.os.AsyncTask;

import com.openapi.commons.common.util.CollectionUtils;
import com.openapi.commons.common.util.DateUtils;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.ModelUtils;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.ks.myapp.data.config.FavoriteChangeEvent;

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
        return DBManager.getInstance().getDaoSession().getFavoriteVoDao().loadAll();
    }

    public static FavoriteVo getFavorite(Context context, String url) {
        List<FavoriteVo> list = DBManager.getInstance().getDaoSession().getFavoriteVoDao().queryRaw("where T.ITEM_URL=?", url);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return list.get(0);
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
        void isFavorite(String itemUrl, boolean ret);
    }

    public interface ModFavoriteListener {
        void modFavorite(boolean ret);
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
            DBManager.getInstance().getDaoSession().getFavoriteVoDao().insert(vo);
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
            FavoriteVo vo = getFavorite(context, itemUrl);
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
        private FavoriteVo vo;
        private ModFavoriteListener listener;

        public ModInfoTask(Context context, FavoriteVo vo, ModFavoriteListener listener) {
            this.context = context;
            this.vo = vo;
            this.listener = listener;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            FavoriteVo ret = getFavorite(context, vo.itemUrl);
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

        @Override
        protected void onPostExecute(Boolean ret) {
            if (listener != null)
                listener.modFavorite(ret);
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
