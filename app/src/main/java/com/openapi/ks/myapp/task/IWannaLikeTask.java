package com.openapi.ks.myapp.task;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.openapi.ks.myapp.constant.Constants;
import com.openai.net.BaseService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by openapi on 2016/8/29.
 */
public class IWannaLikeTask {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static String TAG = "IWannaLikeTask";
    BaseService baseService;
    long id;
    private Context context;
    private String url;

    public IWannaLikeTask(Context context, String url, long id) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
        this.url = url;
        this.id = id;
    }

    public static void start(Context context, String url, long id) {
        new IWannaLikeTask(context, url, id).execute();
    }

    public void execute() {
        executor.execute(() -> {
            try {
                baseService.postData(String.format(Constants.getUrl(url), id), null, null, null, TAG, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
