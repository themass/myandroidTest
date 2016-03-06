package com.timeline.vpn.common.net;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.Network;
import com.android.volley.NetworkError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RequestQueue.RequestFilter;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.ImageLoader;
import com.timeline.vpn.R;
import com.timeline.vpn.common.util.cache.BitmapLruCache;
import com.timeline.vpn.common.util.cache.DiskBasedCacheEx;

import java.io.File;

/**
 * Volley
 */
public class VolleyUtils {
    // 缓存目录
    private static final String DEFAULT_CACHE_DIR = "timeline_vpn";

    private static Context context;
    private static RequestQueue mRequestQueue;
    private static ImageLoader mImageLoader;

    public static void init(Context context) {
        VolleyUtils.context = context;
        mRequestQueue = newRequestQueue(context, null);
        mImageLoader = new ImageLoader(mRequestQueue, new BitmapLruCache(context));
        mRequestQueue.start();
    }

    private static RequestQueue newRequestQueue(Context context, HttpStack stack) {
        if (stack == null) {
            stack = new OkHttp3Stack();
        }
        Network network = new BasicNetwork(stack);
        File cacheDir = new File(context.getExternalCacheDir(), DEFAULT_CACHE_DIR);
        return new RequestQueue(new DiskBasedCacheEx(cacheDir, 50 * 1024 * 1024), network, 6); //修改为6个线程
    }

    /**
     * 处理网络请求异常
     */
    public static void showVolleyError(VolleyError volleyError) {
        Toast.makeText(context, getVolleyError(volleyError), Toast.LENGTH_SHORT).show();
    }

    public static int getVolleyError(VolleyError volleyError) {
        int msgId = R.string.error_network_unknown;
        if (volleyError instanceof NetworkError) {
            msgId = R.string.error_network_no_connection;
        } else if (volleyError instanceof TimeoutError) {
            msgId = R.string.error_network_timeout;
        }
        return msgId;
    }

    /**
     * 将请求加入Volley队列
     */
    public static void addRequest(Request<?> request) {
        mRequestQueue.add(request);
    }

    /**
     * 取消Volley请求
     */
    public static void cancelRequest(String tag) {
        mRequestQueue.cancelAll(tag);
    }

    /**
     * 取消Volley请求
     */
    public static void cancelRequest(RequestFilter filter) {
        mRequestQueue.cancelAll(filter);
    }

    /**
     * 获取Volley图片请求加载器
     */
    public static ImageLoader getImageLoader() {
        return mImageLoader;
    }


}
