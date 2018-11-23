package com.qq.vpn.support.task;

import android.content.Context;
import android.os.AsyncTask;

import com.qq.Constants;
import com.qq.vpn.support.NetApiUtil;

/**
 * Created by themass on 2016/8/29.
 */
public class IWannaLikeTask extends AsyncTask {
    private static String TAG = "IWannaLikeTask";
    NetApiUtil api;
    long id;
    private Context context;
    private String url;

    public IWannaLikeTask(Context context, String url, long id) {
        this.context = context;
        api = new NetApiUtil(context);
        this.url=url;
        this.id = id;
    }

    public static void start(Context context, String url,long id) {
        new IWannaLikeTask(context, url,id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        api.postData(String.format(Constants.getUrl(url), id), null, null, null, TAG, null);
        return null;
    }
}
