package com.openapi.core.legacyui.features;

import android.app.Activity;
import android.content.Context;
import java.util.concurrent.CompletableFuture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;

import com.openapi.common.net.http.ApiRequest;
import com.openapi.common.net.http.NetError;
import com.openapi.core.legacyui.app.BaseFragment;
import com.openapi.common.util.lang.CollectionUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.ViewUtils;
import com.openapi.common.net.http.HttpClient;
import com.openapi.core.R;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.common.net.http.BaseService;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;


/**
 * 支持异步数据加载的fragment
 */
public abstract class LoadableFragment<T> extends BaseFragment {
    public boolean mDataLoaded = false;
    public boolean isDestory = false;
    protected ProgressBar mLoadingView;
    protected View mLoadRetryView;
    protected ViewGroup mContentView;
    protected T mData; // 异步加载的数据
    protected Handler handler = new Handler(Looper.getMainLooper());
    // protected boolean mPullToRefreshEnable = true;
    protected Context mContext;
    /** 每次发起查询或取消时加一；回调里发现 token 已过期就丢弃结果。 */
    private int mQueryToken;

    View.OnClickListener mRefreshClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            startQuery(true);
        }
    };
    private int DEFAULT_LAYOUT = R.layout.layout_search_load_list_view;

    private int fragmentLayoutId = DEFAULT_LAYOUT;
    private TextView tvRetry;
    protected BaseService indexService;
    public SearchView mSearchView;
    public String keyword = "";

    @Override
    @Deprecated
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    protected int getRootViewId() {
        return DEFAULT_LAYOUT;
    }

    protected boolean showSearchView() {
        return false;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        indexService = new BaseService();
        indexService.setup(getActivity());
        mLoadingView = ViewUtils.find(view, R.id.loading);
        mLoadRetryView = ViewUtils.find(view, R.id.load_retry);
        mContentView = ViewUtils.find(view, R.id.content);
        tvRetry = ViewUtils.find(view, R.id.tv_retry);
        mSearchView = ViewUtils.find(view, R.id.searchView);
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        onContentViewCreated(inflater, mContentView, savedInstanceState);
        mLoadRetryView.setOnClickListener(mRefreshClickListener);
        super.setupViews(view, savedInstanceState);
        if (showSearchView()) {
            mSearchView.setVisibility(View.VISIBLE);
        } else {
            mSearchView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mData == null) {
            startQuery(true);
        } else {
            if (isDestory) {
                onDataLoaded(mData);
                hideLoading();
            }
        }
        isDestory = false;
    }

    protected abstract void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState);

    @Override
    public void onDestroyView() {
        isDestory = true;
        cancelQuery();
        mLoadingView = null;
        mContentView = null;
        super.onDestroyView();
    }

    protected void cancelVolley(final String tag) {
        HttpClient.cancelRequest(new HttpClient.RequestFilter() {
            @Override
            public boolean apply(ApiRequest<?> request) {
                if (request.getTag() == null) {
                    return false;
                } else {
                    return (request.getTag().toString().startsWith(tag));
                }

            }
        });
    }

    protected void startQuery(boolean showLoading) {
        final int token = ++mQueryToken;
        if (showLoading) {
            showLoading();
        }
        loadData(mContext).whenComplete((data, error) -> handler.post(() -> {
            if (token != mQueryToken) {
                return;
            }
            if (error != null) {
                LogUtil.e(error);
                hideLoading();
                if (!mDataLoaded) {
                    showRetry();
                }
                return;
            }
            hideLoading();
            if (data != null) {
                setData(data);
                onDataLoaded(data);
                mDataLoaded = true;
            } else if (!mDataLoaded) {
                showRetry();
            }
        }));
    }

    /** 使还在进行中的查询结果失效（页面销毁、或发起新查询时）。 */
    private void cancelQuery() {
        mQueryToken++;
    }

    public void refresh() {
        startQuery(false);
    }

    /**
     * onDestroyView() 会把 mLoadingView 等视图置空，但 QueryTask 的结果仍可能回到主线程调用这里，
     * 此时直接解引用就会 NPE 把进程带崩。所有视图切换都必须先做空判断。
     */
    private boolean isViewDetached() {
        return mLoadingView == null || mLoadRetryView == null || mContentView == null;
    }

    protected void showLoading() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.VISIBLE);
        mLoadRetryView.setVisibility(View.GONE);
        mContentView.setVisibility(View.GONE);
    }

    protected void hideLoading() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.GONE);
        mContentView.setVisibility(View.VISIBLE);
    }

    protected void showRetry() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(com.openapi.common.R.string.error_network_retry);
    }

    protected void showNodataRetry() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(com.openapi.common.R.string.error_network_nodata_retry);
    }

    protected void setData(T data) {
        this.mData = data;
        if (data instanceof InfoListVo && CollectionUtils.isEmpty(((InfoListVo) mData).voList)) {
            showNodataRetry();
        } else if (data instanceof List && CollectionUtils.isEmpty(((List) mData))) {
            showNodataRetry();
        } else if (data instanceof Map && CollectionUtils.isEmpty(((Map) mData))) {
            showNodataRetry();
        }
    }

    /**
     * 数据加载完成后，绑定ui，在ui线程中执行
     */
    protected abstract void onDataLoaded(T data);

    /**
     * 异步加载数据，在非ui线程中执行
     *
     * @param context context
     * @return 返回数据，返回null表示错误，方法中需要catch所有异常
     */
    /** 加载数据（非阻塞）。future 以异常完成时按失败处理。 */
    protected abstract CompletableFuture<T> loadData(Context context);

}
