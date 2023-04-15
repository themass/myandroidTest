package com.openapi.ks.myapp.task;

import android.content.Context;
import android.os.AsyncTask;

import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;

/**
 * Created by openapi on 2016/8/29.
 */
public class IWannaLikeTask extends AsyncTask {
    private static String TAG = "IWannaLikeTask";
    BaseService baseService;
    long id;
    private Context context;
    private String url;

    public IWannaLikeTask(Context context, String url,long id) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
        this.url=url;
        this.id = id;
    }

    public static void start(Context context, String url,long id) {
        new IWannaLikeTask(context, url,id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        baseService.postData(String.format(Constants.getUrl(url), id), null, null, null, TAG, null);
        return null;
    }
}
