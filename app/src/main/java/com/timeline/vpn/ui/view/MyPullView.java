package com.timeline.vpn.ui.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.timeline.vpn.R;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by themass on 2016/9/6.
 */
public class MyPullView extends LinearLayout {
    @Nullable
    @Bind(R.id.footer_view)
    View footerView;
    @Nullable
    @Bind(R.id.rv_content)
    RecyclerView rvContent;
    @Bind(R.id.srl_layout)
    SwipeRefreshLayout refreshLayout;
    private OnRefreshListener listener;

    public MyPullView(Context context) {
        super(context);
        setupView();
    }

    public MyPullView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyPullView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    public MyPullView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupView();
    }


    public void setListener(OnRefreshListener listener) {
        this.listener = listener;
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.layout_pullrefresh_view, null);
        addView(myView);
        ButterKnife.bind(this);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!isLoadMore())
                    loadData(OnRefreshListener.FRESH);
                else {
                    refreshLayout.setRefreshing(false);
                }
            }
        });
        rvContent.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoadMore() && !refreshLayout.isRefreshing()) {
                    int count = rvContent.getAdapter().getItemCount();
                    if (rvContent.getLayoutManager() instanceof StaggeredGridLayoutManager) {
                        int[] visibleItems = ((StaggeredGridLayoutManager) rvContent.getLayoutManager()).findLastVisibleItemPositions(null);
                        int lastitem = Math.max(visibleItems[0], visibleItems[1]);
                        if ((lastitem > count - 5) && listener.needLoad()) {
                            loadData(OnRefreshListener.LOADMORE);
                            footerView.setVisibility(View.VISIBLE);
                        }
                    } else if (rvContent.getLayoutManager() instanceof LinearLayoutManager) {
                        if ((((LinearLayoutManager) rvContent.getLayoutManager()).findLastVisibleItemPosition() > count - 3) && listener.needLoad()) {
                            loadData(OnRefreshListener.LOADMORE);
                            footerView.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        });
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        rvContent.addItemDecoration(itemDecoration);
    }

    public void setRefresh(boolean flag) {
        refreshLayout.setRefreshing(flag);
    }

    private void loadData(int type) {
        if (listener != null) {
            listener.onRefresh(type);
        } else {
            refreshLayout.setRefreshing(false);
            footerView.setVisibility(View.GONE);
        }
    }

    public boolean isRefreshing() {
        return refreshLayout.isRefreshing();
    }

    public boolean isLoadMore() {
        return footerView.getVisibility() == View.VISIBLE;
    }

    public RecyclerView getRecyclerView() {
        return rvContent;
    }

    public void setLayoutManager(RecyclerView.LayoutManager layout) {
        rvContent.setLayoutManager(layout);
    }

    public void setItemAnimator(RecyclerView.ItemAnimator animator) {
        rvContent.setItemAnimator(animator);
    }

    public void setAdapter(RecyclerView.Adapter adapter) {
        rvContent.setAdapter(adapter);
    }

    public void notifyDataSetChanged() {
        refreshLayout.setRefreshing(false);
        footerView.setVisibility(View.GONE);
        rvContent.getAdapter().notifyDataSetChanged();
    }

    public interface OnRefreshListener {
        public static final int FRESH = 1;
        public static final int LOADMORE = 2;

        public void onRefresh(int type);

        public boolean needLoad();
    }
}
