package com.openapi.commons.common.util.cache;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.google.gson.Gson;


/**
 * Created by wjying on 13-12-22.
 */
public class CacheTask<T> extends AsyncTask<Void, Void, T> {
    private final Gson gson = new Gson();
    private Context mContext;
    private String mUrl;
    private CacheTaskResponse mCacheTaskResponse;

    public CacheTask(Context context, String url, CacheTaskResponse cacheTaskResponse) {
        mContext = context.getApplicationContext();
        mUrl = url;
        mCacheTaskResponse = cacheTaskResponse;
    }

    @Override
    protected final T doInBackground(Void... params) {
        if (TextUtils.isEmpty(mUrl)) {
            return null;
        }
        SimpleDiskCache.StringEntry entry = null;
        try {
            entry = SimpleDiskCacheUtils.getSimpleDiskCache(mContext).getString(mUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parse(entry != null ? entry.getString() : null);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        mCacheTaskResponse = null;
    }

    @Override
    protected final void onPostExecute(T t) {
        if (mCacheTaskResponse != null) {
            mCacheTaskResponse.onResponse(t);
        }
    }

    protected T parse(String cache) {
        return null;
    }

    protected Context getContext() {
        return mContext;
    }

    protected Gson getGson() {
        return this.gson;
    }

    /**
     * 异步取缓存回调接口
     *
     * @param <T>
     */
    public interface CacheTaskResponse<T> {
        void onResponse(T t);
    }
}
