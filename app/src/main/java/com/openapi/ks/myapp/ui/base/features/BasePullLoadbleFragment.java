package com.openapi.ks.myapp.ui.base.features;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.appcompat.widget.SearchView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.core.net.request.InfoListVo;
import com.openapi.core.net.BaseService;
import com.openapi.ks.chat.R;
import com.openapi.core.view.view.DividerItemDecoration;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.StringUtils;
import com.openapi.ks.myapp.ui.gsy.compose.ListShellState;
import com.openapi.ks.myapp.ui.gsy.compose.ListShellView;
import com.openapi.ks.myapp.ui.gsy.compose.ListStateOverlay;

/**
 * Created by openapi on 2016/8/12.
 *
 * 列表外壳已迁移到 Compose（{@link ListShellView} 负责下拉刷新、加载更多条和原生 RecyclerView；
 * {@link ListStateOverlay} 负责加载中 / 失败重试 / 无数据）。状态机仍在 {@link LoadableFragment}，
 * 这里只重写它的视图输出，不改动数据流与分页逻辑。
 */
public abstract class BasePullLoadbleFragment<T> extends LoadableFragment<InfoListVo<T>> implements ListShellView.OnRefreshListener, BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<T> {
    @Nullable
    public ListShellView pullView;
    /** 全屏视频容器：只有部分子页面的布局里有它，没有时为 null。 */
    @Nullable
    public FrameLayout fullContainer;
    /** 加载中 / 重试 / 无数据覆盖层，与 pullView 同属 content 的父布局，画在 content 之上。 */
    @Nullable
    private ListStateOverlay stateOverlay;

    public InfoListVo<T> infoListVo = new InfoListVo<T>();

    /** 子类重写时必须先调用 super：下拉列表控件来自这里填充的 common_mypage_view。 */
    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        // 布局里的 id 必须是 ListShellView；类型不符时置空而不是强转抛 ClassCastException。
        View shell = view.findViewById(R.id.my_pullview);
        pullView = shell instanceof ListShellView ? (ListShellView) shell : null;
        fullContainer = view.findViewById(R.id.video_full_container);
    }

    @Override
    protected void unbindViews() {
        pullView = null;
        fullContainer = null;
        stateOverlay = null;
        super.unbindViews();
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        initStateOverlay();
        initPullView();
        mSearchView.setSubmitButtonEnabled(true);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // 当点击搜索按钮时触发该方法
            @Override
            public boolean onQueryTextSubmit(String query) {
                LogUtil.i("搜" + query);
                keyword = query;
                pullView.setRefresh(true);
                mSearchView.clearFocus();
                return false;
            }

            // 当搜索内容改变时触发该方法
            @Override
            public boolean onQueryTextChange(String newText) {
                //没有内容时 在查询
                if (!StringUtils.hasText(newText) && StringUtils.hasText(keyword)) {
                    LogUtil.i("清空文字");
                    keyword = null;
//                    pullView.setRefresh(true);
//                    mSearchView.clearFocus();
                }
                return false;
            }
        });
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSearchView.clearFocus();
    }

    /**
     * 把 Compose 覆盖层挂到 content 的父布局上（与 content 同级、在其之上）。
     * XML 里的 ProgressBar 默认可见，这里必须隐藏，否则会和 Compose 的加载圈叠在一起。
     */
    private void initStateOverlay() {
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.GONE);
        }
        if (mLoadRetryView != null) {
            mLoadRetryView.setVisibility(View.GONE);
        }
        if (mContentView == null || !(mContentView.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup stateRoot = (ViewGroup) mContentView.getParent();
        ListStateOverlay overlay = new ListStateOverlay(getContext());
        overlay.setOnRetryClick(() -> startQuery(true));
        stateRoot.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stateOverlay = overlay;
        // 与原实现一致：首次进入时先是加载中，content 隐藏。
        applyShellState(ListShellState.LOADING);
    }

    // 以下四个方法替代 LoadableFragment 原来的 XML 视图切换，显隐规则与原实现逐条对应：
    // showLoading: loading 显示、retry 隐藏、content 隐藏
    // hideLoading: loading 隐藏、retry 隐藏、content 显示
    // showRetry / showNodataRetry: loading 隐藏、retry 显示（文案不同）、content 隐藏

    @Override
    protected void showLoading() {
        applyShellState(ListShellState.LOADING);
    }

    @Override
    protected void hideLoading() {
        applyShellState(ListShellState.CONTENT);
    }

    @Override
    protected void showRetry() {
        applyShellState(ListShellState.RETRY);
    }

    @Override
    protected void showNodataRetry() {
        applyShellState(ListShellState.NODATA);
    }

    private void applyShellState(ListShellState state) {
        // onDestroyView 之后 content / 覆盖层会被置空，异步回调到这里必须直接返回。
        if (mContentView == null || stateOverlay == null) {
            return;
        }
        mContentView.setVisibility(state == ListShellState.CONTENT ? View.VISIBLE : View.GONE);
        stateOverlay.setState(state);
    }

    protected void initPullView() {
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pullView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, com.openapi.core.util.R.drawable.divider_item);
        pullView.setAdapter(getAdapter());
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        pullView.setListener(this);
    }

    @Override
    protected void onDataLoaded(InfoListVo<T> data) {
        if (pullView != null) {
            if (data != null) {
                if (pullView.isLoadMore()) { //上拉加载
                    loadMoreData(data);
                } else { //下拉刷新 或者首次
                    freshData(data);
                }
                infoListVo.copy(data);
                initSort();
                sortData();
                data.voList.clear();
                data.voList.addAll(infoListVo.voList);
                setData(data);
                LogUtil.i("mData size=" + infoListVo.voList.size());
            }
            pullView.notifyDataSetChanged();
            mSearchView.clearFocus();
        }
    }

    protected void sortData() {
    }

    protected void initSort() {
    }

    protected void loadMoreData(InfoListVo<T> data) {
        infoListVo.voList.addAll(data.voList);
    }

    protected void freshData(InfoListVo<T> data) {
        infoListVo.voList.clear();
        infoListVo.voList.addAll(data.voList);
    }

    @Override
    public void onItemClick(View view, T data, int postion) {
        pullView.notifyDataSetChanged();
    }

    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == ListShellView.FRESH)
            infoListVo.pageNum = 0;
        startQuery(false);
    }

    @Override
    public boolean needLoad() {
        return infoListVo.hasMore;
    }

    protected abstract BaseRecyclerViewAdapter getAdapter();

}
