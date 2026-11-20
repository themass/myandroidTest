package com.openapi.feature.content.common;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 整改清单 P3-21：列表页「看过置灰」的内存副本。
 *
 * 以前每次 onBindViewHolder 都同步查一次 Room（{@code HistoryUtil.getHistory}），
 * 关掉 allowMainThreadQueries 后那种读会直接崩。改成：
 * 进页面时后台一次性 {@code loadFrom} 全部已访问地址，点击时 {@code markVisited}
 * 补一条，bind 只查这个内存集合。
 *
 * 线程约定：{@link #loadFrom} 在主线程回调里调用，{@link #markVisited} 从点击线程调用，
 * bind 在主线程读——用同步块保证集合本身的可见性，读操作足够便宜。
 */
public final class VisitedUrlCache {

    private final Set<String> visited = new HashSet<>();

    /** 用后台读到的完整列表替换当前内容（空列表也是有效结果）。 */
    public synchronized void loadFrom(@Nullable Collection<String> urls) {
        visited.clear();
        if (urls != null) {
            for (String url : urls) {
                if (url != null) visited.add(url);
            }
        }
    }

    /** 刚点开的地址立刻算已访问，不等数据库落盘。 */
    public synchronized void markVisited(@Nullable String url) {
        if (url != null) visited.add(url);
    }

    public synchronized boolean isVisited(@Nullable String url) {
        return url != null && visited.contains(url);
    }
}
