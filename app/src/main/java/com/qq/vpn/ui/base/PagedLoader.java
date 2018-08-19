package com.qq.vpn.ui.base;

import com.qq.vpn.domain.res.InfoListVo;

/**
 */
public interface PagedLoader<T> {
    int PAGE_SIZE = 20;
    int STATE_INIT = -1;
    int STATE_NORMAL = 0;
    int STATE_LOADING = 1;
    int STATE_ERROR = 2;
    int STATE_EMPTY = 3;

    /**
     * 加载下一页数据
     */
    void loadNextPage();

    /**
     * 返回已加载的所有数据
     */
    InfoListVo<T> getData();

    int getCount();

    T getItem(int position);

    void setPageLoadListener(PageLoadListener listener);

    /**
     * 刷新，在刷新完成后替换数据
     */
    void refresh();

    /**
     * 重试加载
     */
    void retry();

    /**
     * 显示当前loader的状态
     */
    void initState();

    boolean isInit();

    boolean needAutoRefresh();

    /**
     * 只清除数据
     */
    void clear();

    int currentSize();

    interface PageLoadListener {
        void onPageLoaded();

        void onPageLoading();

        void onEmpty();

        void onError();
    }
}
