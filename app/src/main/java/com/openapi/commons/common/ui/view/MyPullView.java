package com.openapi.commons.common.ui.view;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

import com.openapi.ks.moviefree1.R;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by openapi on 2016/9/6.
 */
public class MyPullView extends LinearLayout {
    @Nullable
    @BindView(R.id.footer_view)
    View footerView;
    @Nullable
    @BindView(R.id.rv_content)
    RecyclerView rvContent;
    @BindView(R.id.srl_layout)
    SwipeRefreshLayout refreshLayout;
    //    Snackbar bar = null;
    TranslateAnimation mShowAction;
    TranslateAnimation mHiddenAction;
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
                            showLoadingLabel();
                        }
                    } else if (rvContent.getLayoutManager() instanceof LinearLayoutManager) {
                        if ((((LinearLayoutManager) rvContent.getLayoutManager()).findLastVisibleItemPosition() > count - 3) && listener.needLoad()) {
                            loadData(OnRefreshListener.LOADMORE);
                            showLoadingLabel();
                        }
                    }
                }
            }
        });
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        rvContent.addItemDecoration(itemDecoration);
        mShowAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        mShowAction.setDuration(500);
        mHiddenAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                1.0f);
        mHiddenAction.setDuration(500);
//        bar = Snackbar.make(rvContent,R.string.load,Snackbar.LENGTH_INDEFINITE);
//        View view = bar.getView();//获取Snackbar的view
//        if(view!=null){
//            view.setBackgroundColor(getResources().getColor(R.color.style_color_primary));//修改view的背景色
//            ((TextView) view.findViewById(R.id.snackbar_text)).setTextColor(getResources().getColor(R.color.fab_color_shadow));//获取Snackbar的message控件，修改字体颜色
//        }
    }

    public void showLoadingLabel() {
        footerView.startAnimation(mShowAction);
        footerView.setVisibility(View.VISIBLE);
//        if(bar!=null&&!bar.isShown())
//            bar.show();
    }

    public void hindLoadingLabel() {
        footerView.startAnimation(mHiddenAction);
        footerView.setVisibility(View.GONE);
//        if(bar!=null&&bar.isShown())
//            bar.dismiss();
    }

    public void setLongClickListener(OnLongClickListener l) {
        rvContent.setOnLongClickListener(l);
    }

    public void setRefresh(boolean flag) {
        refreshLayout.setRefreshing(flag);
        if (flag) {
            loadData(OnRefreshListener.FRESH);
        }
    }

    private void loadData(int type) {
        if (listener != null) {
            listener.onRefresh(type);
        } else {
            refreshLayout.setRefreshing(false);
            hindLoadingLabel();
        }
    }

    public boolean isRefreshing() {
        return refreshLayout.isRefreshing();
    }

    public boolean isLoadMore() {
        return footerView.getVisibility() == View.VISIBLE;
//        return bar.isShown();
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
        hindLoadingLabel();
        rvContent.getAdapter().notifyDataSetChanged();
    }

    public interface OnRefreshListener {
        int FRESH = 1;
        int LOADMORE = 2;

        void onRefresh(int type);

        boolean needLoad();
    }
}
