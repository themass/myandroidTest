package com.timeline.vpn.ui.base;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.timeline.vpn.R;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.util.ViewUtils;

import java.lang.ref.WeakReference;


/**
 * 支持异步数据加载的fragment
 *
 * @author jrzheng
 */
public abstract class LoadableFragment<T> extends BaseFragment {
    protected ProgressBar mLoadingView;
    protected View mLoadRetryView;
    protected ViewGroup mContentView;
    private QueryTask<T> mQueryTask;
    protected T mData; // 异步加载的数据
    protected Handler handler = new Handler();
    // protected boolean mPullToRefreshEnable = true;
    protected Context mContext;
    private  int DEFAULT_LAYOUT = R.layout.base_loadable_fragment;
    private  int fragmentLayoutId = DEFAULT_LAYOUT;
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    protected int getRootViewId() {
        return DEFAULT_LAYOUT;
    }

    @Override
    protected void setupViews(View view,Bundle savedInstanceState) {
        mLoadingView = ViewUtils.find(view, R.id.loading);
        mLoadRetryView = ViewUtils.find(view, R.id.load_retry);
        mContentView = ViewUtils.find(view,R.id.content);
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        onContentViewCreated(inflater, mContentView, savedInstanceState);
        mLoadRetryView.setOnClickListener(mRefreshClickListener);
        super.setupViews(view,savedInstanceState);
    }

    View.OnClickListener mRefreshClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            startQuery(true);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (mData == null) {
            startQuery(true);
        } else {
            onDataLoaded(mData);
            hideLoading();
        }
    }

    protected abstract void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState);

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
        mQueryTask = new QueryTask<T>(this, showLoading, handler);
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
    }

    protected void setData(T data) {
        this.mData = data;
    }

    private static class QueryTask<T> extends AsyncTask<Void, Void, T> {

        private Handler handler;
        private Context mContext;
        private WeakReference<LoadableFragment<T>> mFragment;
        private boolean mShowLoading; // 下拉刷新时，不需要再展示loading

        QueryTask(LoadableFragment<T> fragment, boolean showLoading, Handler handler) {
            this.mContext = fragment.getActivity();
            mFragment = new WeakReference<LoadableFragment<T>>(fragment);
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
                    e.printStackTrace();
                    showError(e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(T data) {
            LoadableFragment<T> fragment = mFragment != null ? mFragment.get() : null;
            if (fragment != null) {
                fragment.hideLoading();
                if (data != null) {
                    fragment.setData(data);
                    fragment.onDataLoaded(data);
                    // fragment.mDataLoaded = true;
                } else {
                    fragment.showRetry();
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
