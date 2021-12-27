package com.qq.yewu.net;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.Network;
import com.android.volley.NetworkError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RequestQueue.RequestFilter;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.ImageLoader;
import com.qq.common.exce.MyVolleyError;
import com.qq.common.util.FileUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.cache.BitmapLruCache;
import com.qq.common.util.cache.DiskBasedCacheEx;
import com.qq.ks1.R;
import com.qq.myapp.base.MyApplication;

import java.io.File;

/**
 * Volley
 */
public class VolleyUtils {
    // 缓存目录
    private static Context context;
    private static RequestQueue mRequestQueue;
    private static ImageLoader mImageLoader;

    public static void init() {
        VolleyLog.DEBUG = MyApplication.isDebug;
        VolleyLog.setTag("VolleyUtils");
        VolleyUtils.context = MyApplication.getInstance();
        mRequestQueue = newRequestQueue(context, null);
        mImageLoader = new ImageLoader(mRequestQueue, new BitmapLruCache(context));
        start();
    }
    public static void  start(){
        mRequestQueue.start();
    }

    private static RequestQueue newRequestQueue(Context context, HttpStack stack) {
        if (stack == null) {
            stack = new OkHttpStack();
        }
        Network network = new BasicNetwork(stack);
        File cacheDir = new File(FileUtils.getWriteFilePath(context), FileUtils.VOLLEY_PATH);
        return new RequestQueue(new DiskBasedCacheEx(cacheDir, 50 * 1024 * 1024), network, 6); //修改为6个线程
    }

    /**
     * 处理网络请求异常
     */
    public static void showVolleyError(VolleyError volleyError) {
        LogUtil.e(volleyError.getMessage(), volleyError);
        Toast.makeText(context, getVolleyError(volleyError), Toast.LENGTH_SHORT).show();
    }

    public static String getVolleyError(VolleyError volleyError) {
        if (volleyError instanceof NetworkError) {
            return context.getResources().getString(R.string.error_network_no_connection);
        } else if (volleyError instanceof TimeoutError) {
            return context.getResources().getString(R.string.error_network_timeout);
        } else if (volleyError instanceof MyVolleyError) {
            return ((MyVolleyError) volleyError).getMsg();
        }
        return context.getResources().getString(R.string.error_network_unknown);
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
        LogUtil.i("cancelRequest tag=" + tag);
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
