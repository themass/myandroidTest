package com.openapi.ks.myapp.ui.base.features;

import android.app.Activity;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;

import com.openapi.core.net.NetRequest;
import com.openapi.core.net.NetError;
import com.openapi.ks.myapp.ui.base.app.BaseFragment;
import com.openapi.core.util.CollectionUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.ViewUtils;
import com.openapi.core.net.NetQueue;
import com.openapi.ks.chat.R;
import com.openapi.core.net.request.InfoListVo;
import com.openapi.core.net.BaseService;

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
    private ExecutorService mExecutorService;

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
        NetQueue.cancelRequest(new NetQueue.RequestFilter() {
            @Override
            public boolean apply(NetRequest<?> request) {
                if (request.getTag() == null) {
                    return false;
                } else {
                    return (request.getTag().toString().startsWith(tag));
                }

            }
        });
    }

    protected void startQuery(boolean showLoading) {
        cancelQuery();
        mExecutorService = Executors.newSingleThreadExecutor();
        
        if (showLoading) {
            showLoading();
        }
        
        mExecutorService.execute(() -> {
            try {
                T data = loadData(mContext);
                handler.post(() -> {
                    hideLoading();
                    if (data != null) {
                        setData(data);
                        onDataLoaded(data);
                        mDataLoaded = true;
                    } else {
                        if (!mDataLoaded) {
                            showRetry();
                        } else {
                            hideLoading();
                        }
                    }
                });
            } catch (Exception e) {
                LogUtil.e(e);
                handler.post(() -> {
                    hideLoading();
                    if (!mDataLoaded) {
                        showRetry();
                    }
                });
            }
        });
    }

    private void cancelQuery() {
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
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

    private void showRetry() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(com.openapi.core.util.R.string.error_network_retry);
    }

    private void showNodataRetry() {
        if (isViewDetached()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(com.openapi.core.util.R.string.error_network_nodata_retry);
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
    protected abstract T loadData(Context context) throws Exception;

}
