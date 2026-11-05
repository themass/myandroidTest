package com.openai.commonutil;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.openai.commonevent.FavoriteChangeEvent;
import com.openai.commonevent.FavoriteVo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        return DBManager.getInstance().favoriteDao().localFavorites();
    }

    public static FavoriteVo getFavorite(Context context, String url) {
        try {
            List<FavoriteVo> list = DBManager.getInstance().favoriteDao().byUrl(url);
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
        DBManager.getInstance().favoriteDao().deleteAll();
    }

    public interface GetFavoriteListener {
        void isFavorite(String itemUrl, FavoriteVo vo);
    }

    public interface ModFavoriteListener {
        void modFavorite(FavoriteVo vo);
    }

    public static class AddInfoTask {
        private static final ExecutorService executor = Executors.newCachedThreadPool();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        private Context context;
        private FavoriteVo vo;

        public AddInfoTask(Context context, FavoriteVo vo) {
            this.context = context;
            this.vo = vo;
        }

        public void execute() {
            executor.execute(() -> {
                try {
                    DBManager.getInstance().favoriteDao().insertOrReplace(vo);
                    mainHandler.post(() -> {
                        // 任务完成后的UI更新（如果需要）
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public static class DelInfoTask {
        private static final ExecutorService executor = Executors.newCachedThreadPool();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        private Context context;
        private long id;

        public DelInfoTask(Context context, long id) {
            this.context = context;
            this.id = id;
        }

        public void execute() {
            executor.execute(() -> {
                try {
                    DBManager.getInstance().favoriteDao().deleteById(id);
                    mainHandler.post(() -> {
                        // 任务完成后的UI更新（如果需要）
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public static class GetInfoTask {
        private static final ExecutorService executor = Executors.newCachedThreadPool();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        private Context context;
        private String itemUrl;
        private GetFavoriteListener listener;

        public GetInfoTask(Context context, String itemUrl, GetFavoriteListener listener) {
            this.context = context;
            this.itemUrl = itemUrl;
            this.listener = listener;
        }

        public void execute() {
            executor.execute(() -> {
                try {
                    FavoriteVo result = getFavorite(context, itemUrl);
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.isFavorite(itemUrl, result);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.isFavorite(itemUrl, null);
                        }
                    });
                }
            });
        }
    }

    public static class ModInfoTask {
        private static final ExecutorService executor = Executors.newCachedThreadPool();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        private Context context;
        private FavoriteVo vo;
        private ModFavoriteListener listener;

        public ModInfoTask(Context context, FavoriteVo vo, ModFavoriteListener listener) {
            this.context = context;
            this.vo = vo;
            this.listener = listener;
        }

        public void execute() {
            executor.execute(() -> {
                try {
                    FavoriteVo ret = getFavorite(context, vo.itemUrl);
                    FavoriteVo result;
                    if (ret == null) {
                        vo.setExtra(ModelUtils.entry2Json(vo.getO()));
                        vo.setUpdateTime(DateUtils.format(new Date(), DateUtils.DATE_FORMAT));
                        addFavorite(context, vo);
                        EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                        result = vo;
                    } else {
                        EventBusUtil.getEventBus().post(new FavoriteChangeEvent());
                        delFavorite(context, ret.id);
                        result = null;
                    }
                    
                    final FavoriteVo finalResult = result;
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.modFavorite(finalResult);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.modFavorite(null);
                        }
                    });
                }
            });
        }
    }
}
