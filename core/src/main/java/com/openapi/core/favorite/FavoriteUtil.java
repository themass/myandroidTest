package com.openapi.core.favorite;

import com.openapi.common.util.android.Background;
import com.openapi.common.util.log.LogUtil;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.openapi.core.db.FavoriteVo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.openapi.common.util.lang.CollectionUtils;
import com.openapi.common.util.lang.DateUtils;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.core.favorite.FavoriteStore;

import java.util.Date;
import java.util.List;

public class FavoriteUtil {

    private static volatile FavoriteStore store;

    /** 由 app 在 Application.onCreate 中注入实际的存储实现。 */
    public static void install(FavoriteStore s) {
        store = s;
    }

    private static FavoriteStore store() {
        FavoriteStore s = store;
        if (s == null) {
            throw new IllegalStateException("FavoriteStore 未注入：请在 Application.onCreate 中调用 FavoriteUtil.install(...)");
        }
        return s;
    }

    /**
     * 读取数据库 得到本地收藏。
     *
     * 只许在后台线程调用：AppDatabase 不再 allowMainThreadQueries，
     * 主线程调用会直接抛 IllegalStateException。UI 请用 {@link #getLocalFavoritesAsync}。
     */
    public static List<FavoriteVo> getLocalFavorites(Context context) {
        return store().localFavorites();
    }

    /** 同上：只许在后台线程调用；UI 用 {@link #getFavoriteAsync}。 */
    public static FavoriteVo getFavorite(Context context, String url) {
        try {
            List<FavoriteVo> list = store().byUrl(url);
            if (CollectionUtils.isEmpty(list)) {
                return null;
            }
            return list.get(0);
        }catch (Exception e){
            return null;
        }
    }

    /** 收藏列表回调，一定在主线程触发。 */
    public interface FavoriteListListener {
        void onFavorites(List<FavoriteVo> list);
    }

    /** 单条收藏回调（没命中是 null），一定在主线程触发。 */
    public interface FavoriteVoListener {
        void onFavorite(FavoriteVo vo);
    }

    /**
     * 整改清单 P3-21：收藏表在后台线程读、结果回主线程，UI 侧不再碰 Room。
     * 读表失败按空列表交付（旧同步写法在这种情况下本来会直接崩在 UI 上）。
     */
    public static void getLocalFavoritesAsync(Context context, FavoriteListListener listener) {
        Background.parallel(() -> {
            List<FavoriteVo> list;
            try {
                list = store().localFavorites();
            } catch (Throwable e) {
                LogUtil.e(e);
                list = java.util.Collections.emptyList();
            }
            final List<FavoriteVo> result = list;
            Background.main(() -> listener.onFavorites(result));
        });
    }

    /** 单条收藏的后台读 + 主线程回调；没命中或读失败都交付 null。 */
    public static void getFavoriteAsync(Context context, String url, FavoriteVoListener listener) {
        Background.parallel(() -> {
            FavoriteVo vo = getFavorite(context, url);
            Background.main(() -> listener.onFavorite(vo));
        });
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
        store().deleteAll();
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
                    store().insertOrReplace(vo);
                    mainHandler.post(() -> {
                        // 任务完成后的UI更新（如果需要）
                    });
                } catch (Exception e) {
                    LogUtil.e(e);
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
                    store().deleteById(id);
                    mainHandler.post(() -> {
                        // 任务完成后的UI更新（如果需要）
                    });
                } catch (Exception e) {
                    LogUtil.e(e);
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
                    LogUtil.e(e);
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
                        vo.setExtra(GsonUtils.entry2Json(vo.getO()));
                        vo.setUpdateTime(DateUtils.format(new Date(), DateUtils.DATE_FORMAT));
                        addFavorite(context, vo);
                        com.openapi.core.event.CoreEvents.INSTANCE.postFavoriteChanged();
                        result = vo;
                    } else {
                        com.openapi.core.event.CoreEvents.INSTANCE.postFavoriteChanged();
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
                    LogUtil.e(e);
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
