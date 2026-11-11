package com.openapi.myapp.ui.base.features;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.openapi.common.ui.base.BaseFragment;
import com.openapi.common.util.CollectionUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ViewUtils;
import com.openapi.yewu.net.VolleyUtils;
import com.openapi.myapp.bean.vo.InfoListVo;
import com.openapi.myapp.data.BaseService;
import com.openapi.ks.free1.R;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;


/**
 * 支持异步数据加载的fragment
 */
public abstract class LoadableFragment<T> extends BaseFragment {
    public boolean mDataLoaded = false;
    public boolean isDestory = false;
    /**
     * 首屏加载失败后的静默重试次数。
     * <p>
     * 隧道刚建立的一两秒内，隧道内的 DNS 可能还没生效（实测连上后 1 秒内解析自有 API 域名失败、
     * 再过 1 秒就正常）。这时候打开节点列表，用户看到的就是 "Load error, please click Retry."，
     * 而实际上什么都不用做、等一秒就好。首屏失败自动重试几次，把这段空窗期吃掉。
     * 重试都不成功才让用户点重试，避免把真实的网络故障藏起来。
     */
    private static final int AUTO_RETRY_MAX = 3;
    private static final long AUTO_RETRY_DELAY_MS = 1_200L;
    private int mAutoRetry;
    protected ProgressBar mLoadingView;
    protected View mLoadRetryView;
    protected ViewGroup mContentView;
    protected T mData; // 异步加载的数据
    protected Handler handler = new Handler();
    // protected boolean mPullToRefreshEnable = true;
    protected Context mContext;
    private QueryTask<T> mQueryTask;
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
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    protected int getRootViewId() {
        return DEFAULT_LAYOUT;
    }
    protected boolean showSearchView(){
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
        if(showSearchView()){
            mSearchView.setVisibility(View.VISIBLE);
        }else{
            mSearchView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mData == null) {
            startQuery(true);
        } else {
            if(isDestory) {
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
        VolleyUtils.cancelRequest(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                if (request.getTag() == null) {
                    return false;
                } else {
                    return (request.getTag().toString().startsWith(tag));
                }

            }
        });
    }

    protected void startQuery(boolean showLoading) {
        // 用户/外部发起的加载：重新计数
        startQuery(showLoading, true);
    }

    /**
     * @param resetRetry false 表示这是自动重试本身。自动重试必须保留计数器，
     *                   否则每次重试都清零，{@link #AUTO_RETRY_MAX} 永远不会到达，
     *                   界面会一直停在 loading，真正的故障反而暴露不出来。
     */
    private void startQuery(boolean showLoading, boolean resetRetry) {
        cancelQuery();
        if (resetRetry) {
            mAutoRetry = 0;
        }
        mQueryTask = new QueryTask<>(this, showLoading, handler);
        mQueryTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void cancelQuery() {
        if (mQueryTask != null) {
            mQueryTask.cancel(true);
            mQueryTask = null;
        }
    }

    public void refresh() {
        startQuery(false);
    }

    protected void showLoading() {
        mLoadingView.setVisibility(View.VISIBLE);
        mLoadRetryView.setVisibility(View.GONE);
        mContentView.setVisibility(View.GONE);
    }

    protected void hideLoading() {
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.GONE);
        mContentView.setVisibility(View.VISIBLE);
    }

    private void showRetry() {
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(R.string.error_network_retry);
    }

    private void showNodataRetry() {
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        tvRetry.setText(R.string.error_network_nodata_retry);
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

    private static class QueryTask<T> extends AsyncTask<Void, Void, T> {

        private Handler handler;
        private Context mContext;
        private WeakReference<LoadableFragment<T>> mFragment;
        private boolean mShowLoading; // 下拉刷新时，不需要再展示loading

        QueryTask(LoadableFragment<T> fragment, boolean showLoading, Handler handler) {
            this.mContext = fragment.getActivity();
            mFragment = new WeakReference<>(fragment);
            this.mShowLoading = showLoading;
            this.handler = handler;
        }

        protected void onPreExecute() {
            LoadableFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null && mShowLoading) {
                fragment.showLoading();
            }
        }

        @Override
        protected T doInBackground(Void... params) {
            LoadableFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null) {
                try {
                    return fragment.loadData(mContext);
                } catch (Exception e) {
                    LogUtil.e(e);
                    showError(e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(T data) {
            LoadableFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null && !fragment.getActivity().isFinishing() && !fragment.isDestory) {
                fragment.hideLoading();
                if (data != null) {
                    fragment.setData(data);
                    fragment.onDataLoaded(data);
                    fragment.mDataLoaded = true;
                } else if (!fragment.mDataLoaded) {
                    // 首屏失败：先静默重试，把隧道刚建立时 DNS 尚未生效那段空窗期吃掉
                    if (fragment.mAutoRetry < AUTO_RETRY_MAX) {
                        fragment.mAutoRetry++;
                        LogUtil.i("首屏加载失败，第 " + fragment.mAutoRetry + " 次自动重试");
                        fragment.handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                LoadableFragment<T> f = mFragment != null ? mFragment.get() : null;
                                if (f != null && !f.isDestory && f.getActivity() != null
                                        && !f.getActivity().isFinishing() && !f.mDataLoaded) {
                                    f.startQuery(false, false);
                                }
                            }
                        }, AUTO_RETRY_DELAY_MS);
                    } else {
                        fragment.showRetry();
                    }
                } else {
                    fragment.onDataLoaded(null);
                }
            }
        }

        private void showError(final Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (e instanceof VolleyError) {
                        VolleyUtils.showVolleyError((VolleyError) e);
                    } else if (e.getCause() instanceof VolleyError) {
                        VolleyUtils.showVolleyError((VolleyError) e.getCause());
                    }
                }
            });
        }
    }
}
