package com.timeline.vpn.ui.base;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.timeline.vpn.R;
import com.timeline.vpn.common.exce.RequestException;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.ui.maintab.TabBaseAdsFragment;

import java.lang.ref.WeakReference;

import butterknife.Bind;
import butterknife.OnClick;


/**
 * 支持异步数据加载的fragment
 *
 * @author jrzheng
 */
public abstract class LoadableTabFragment<T> extends TabBaseAdsFragment {
    public boolean mDataLoaded = false;
    protected ViewGroup mContentView;
    protected T mData; // 异步加载的数据
    protected Handler handler = new Handler();
    // protected boolean mPullToRefreshEnable = true;
    protected Context mContext;
    @Bind(R.id.loading)
    ProgressBar mLoadingView;
    @Bind(R.id.load_retry)
    View mLoadRetryView;
    private QueryTask<T> mQueryTask;
    private int DEFAULT_LAYOUT = R.layout.base_loadable_fragment;

    @Override
    protected int getTabBodyViewId() {
        return DEFAULT_LAYOUT;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @OnClick(R.id.load_retry)
    public void onRetryClick(View v) {
        startQuery(true);
    }

    @Override
    public void setUp(View view, LayoutInflater inflater) {
        mContentView = (ViewGroup) view.findViewById(R.id.content);
        onContentViewCreated(inflater, mContentView);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mData == null) {
            LogUtil.i("loadableTabFragment onResume ,start query");
            startQuery(true);
        } else {
            hideLoading();
        }

    }

    protected abstract void onContentViewCreated(LayoutInflater inflater, ViewGroup parent);

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelQuery();
        mLoadingView = null;
        mContentView = null;
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
        cancelQuery();
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
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.VISIBLE);
            mLoadRetryView.setVisibility(View.GONE);
            mContentView.setVisibility(View.GONE);
        }
    }

    protected void hideLoading() {
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.GONE);
            mLoadRetryView.setVisibility(View.GONE);
            mContentView.setVisibility(View.VISIBLE);
        }
    }

    protected void showRetry() {
        mLoadingView.setVisibility(View.GONE);
        mLoadRetryView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
    }

    protected void setData(T data) {
        this.mData = data;
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
        private WeakReference<LoadableTabFragment<T>> mFragment;
        private boolean mShowLoading; // 下拉刷新时，不需要再展示loading

        QueryTask(LoadableTabFragment<T> fragment, boolean showLoading, Handler handler) {
            this.mContext = fragment.getActivity();
            mFragment = new WeakReference<>(fragment);
            this.mShowLoading = showLoading;
            this.handler = handler;
        }

        protected void onPreExecute() {
            LoadableTabFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null && mShowLoading) {
                fragment.showLoading();
            }
        }

        @Override
        protected T doInBackground(Void... params) {
            LoadableTabFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null) {
                try {
                    return fragment.loadData(mContext);
                } catch (Exception e) {
                    showError(e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(T data) {
            LoadableTabFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null) {
                fragment.hideLoading();
                if (data != null) {
                    fragment.setData(data);
                    fragment.onDataLoaded(data);
                    fragment.mDataLoaded = true;
                } else {
                    if (!fragment.mDataLoaded)
                        fragment.showRetry();
                    else {
                        fragment.onDataLoaded(null);
                    }
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
                    } else if (e instanceof RequestException) {
                        RequestException ex = (RequestException) e;
                        LogUtil.e(ex.getErrorMsg(), ex.getException());
                        if (ex.getException() != null) {
                            Toast.makeText(mContext, R.string.error_network_unknown, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mContext, ex.getErrorMsg(), Toast.LENGTH_SHORT).show();
                        }

                    }
                }
            });
        }
    }
}
