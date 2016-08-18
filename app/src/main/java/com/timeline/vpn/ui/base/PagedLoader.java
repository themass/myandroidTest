package com.timeline.vpn.ui.base;

import com.timeline.vpn.bean.vo.InfoListVo;

/**
 * @author jrzheng on 2014/5/4.
 */
public interface PagedLoader<T> {
    static final int PAGE_SIZE = 20;
    static final int STATE_INIT = -1;
    static final int STATE_NORMAL = 0;
    static final int STATE_LOADING = 1;
    static final int STATE_ERROR = 2;
    static final int STATE_EMPTY = 3;

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

    public boolean isInit();

    public boolean needAutoRefresh();

    /**
     * 只清除数据
     */
    public void clear();

    public int currentSize();

    public static interface PageLoadListener {
        void onPageLoaded();

        void onPageLoading();

        void onEmpty();

        void onError();
    }
}
