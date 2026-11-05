package com.openapi.core.util.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by wjying on 13-12-22.
 */
@SuppressWarnings({"deprecation","unchecked"})
public class CacheTask<T> {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private final Gson gson = new Gson();
    private Context mContext;
    private String mUrl;
    private CacheTaskResponse mCacheTaskResponse;
    private boolean isCancelled = false;

    public CacheTask(Context context, String url, CacheTaskResponse cacheTaskResponse) {
        mContext = context.getApplicationContext();
        mUrl = url;
        mCacheTaskResponse = cacheTaskResponse;
    }

    public void execute() {
        executor.execute(() -> {
            if (isCancelled) {
                return;
            }
            
            try {
                if (TextUtils.isEmpty(mUrl)) {
                    mainHandler.post(() -> {
                        if (mCacheTaskResponse != null) {
                            mCacheTaskResponse.onResponse(null);
                        }
                    });
                    return;
                }
                
                SimpleDiskCache.StringEntry entry = null;
                try {
                    entry = SimpleDiskCacheUtils.getSimpleDiskCache(mContext).getString(mUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                T result = parse(entry != null ? entry.getString() : null);
                
                if (!isCancelled) {
                    mainHandler.post(() -> {
                        if (mCacheTaskResponse != null) {
                            mCacheTaskResponse.onResponse(result);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (!isCancelled) {
                    mainHandler.post(() -> {
                        if (mCacheTaskResponse != null) {
                            mCacheTaskResponse.onResponse(null);
                        }
                    });
                }
            }
        });
    }

    public void cancel() {
        isCancelled = true;
        mCacheTaskResponse = null;
    }
    
    public boolean isCancelled() {
        return isCancelled;
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
