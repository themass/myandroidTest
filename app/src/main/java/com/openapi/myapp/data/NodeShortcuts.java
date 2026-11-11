package com.openapi.myapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.openapi.myapp.bean.vo.LocationVo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏节点与最近连接的节点。
 * <p>
 * 只存节点 id，不存名字、地址或密钥：节点内容以服务端下发的列表为准，id 对不上
 * （节点被下线）时自然不再出现。数据只在本机。
 * <p>
 * 排序和增删是纯函数（{@link #arrange}、{@link #pushRecent}、{@link #toggle}），
 * 不碰 Android，便于单元测试；SharedPreferences 只负责读写。
 */
public final class NodeShortcuts {

    /** 最近连接最多保留几个。ExpressVPN 的推荐页也是固定 3 个，再多就和完整列表没区别了。 */
    public static final int RECENT_MAX = 3;

    private static final String PREF = "node_shortcuts";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENT = "recent";

    private NodeShortcuts() {
    }

    // ------------------------------------------------------------ 纯逻辑

    /**
     * 把节点排成：收藏（保持原有相对顺序）→ 最近连接（按最近到最早）→ 其余。
     * 既收藏又在最近里的只出现一次，放在收藏段。不会增删节点。
     */
    @NonNull
    public static List<LocationVo> arrange(@NonNull List<LocationVo> source,
                                           @NonNull Set<Integer> favorites,
                                           @NonNull List<Integer> recent) {
        List<LocationVo> favs = new ArrayList<>();
        List<LocationVo> rest = new ArrayList<>();
        for (LocationVo vo : source) {
            if (favorites.contains(vo.id)) {
                favs.add(vo);
            } else {
                rest.add(vo);
            }
        }
        List<LocationVo> recents = new ArrayList<>();
        for (int id : recent) {
            for (int i = 0; i < rest.size(); i++) {
                if (rest.get(i).id == id) {
                    recents.add(rest.remove(i));
                    break;
                }
            }
        }
        List<LocationVo> out = new ArrayList<>(source.size());
        out.addAll(favs);
        out.addAll(recents);
        out.addAll(rest);
        return out;
    }

    /** 把 id 放到最近列表最前，去重，截断到 {@link #RECENT_MAX}。 */
    @NonNull
    public static List<Integer> pushRecent(@NonNull List<Integer> recent, int id) {
        List<Integer> out = new ArrayList<>();
        out.add(id);
        for (int r : recent) {
            if (r != id && out.size() < RECENT_MAX) {
                out.add(r);
            }
        }
        return out;
    }

    /** 收藏开关：有则去掉，无则加上。返回新集合，不改入参。 */
    @NonNull
    public static Set<Integer> toggle(@NonNull Set<Integer> favorites, int id) {
        Set<Integer> out = new LinkedHashSet<>(favorites);
        if (!out.remove(id)) {
            out.add(id);
        }
        return out;
    }

    // ------------------------------------------------------------ 持久化

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    @NonNull
    public static Set<Integer> favorites(@NonNull Context context) {
        return parseSet(prefs(context).getString(KEY_FAVORITES, ""));
    }

    @NonNull
    public static List<Integer> recent(@NonNull Context context) {
        return new ArrayList<>(parseSet(prefs(context).getString(KEY_RECENT, "")));
    }

    public static boolean isFavorite(@NonNull Context context, int id) {
        return favorites(context).contains(id);
    }

    /** 切换收藏，返回切换后是否为收藏状态。 */
    public static boolean toggleFavorite(@NonNull Context context, int id) {
        Set<Integer> next = toggle(favorites(context), id);
        prefs(context).edit().putString(KEY_FAVORITES, join(next)).apply();
        return next.contains(id);
    }

    /** 记一次连接。id <= 0 是 "Best / Auto" 之类的占位，不是真实节点，不记。 */
    public static void recordConnected(@NonNull Context context, int id) {
        if (id <= 0) {
            return;
        }
        List<Integer> next = pushRecent(recent(context), id);
        prefs(context).edit().putString(KEY_RECENT, join(next)).apply();
    }

    // ------------------------------------------------------------ 序列化

    @NonNull
    static Set<Integer> parseSet(String raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，不能因为一项解析失败丢掉整个列表
            }
        }
        return out;
    }

    @NonNull
    static String join(@NonNull java.util.Collection<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
