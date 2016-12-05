package com.timeline.vpn.task;

import android.content.Context;
import android.os.AsyncTask;

import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;

/**
 * Created by gqli on 2016/8/29.
 */
public class IWannaLikeTask extends AsyncTask {
    private static String TAG = "IWannaLikeTask";
    BaseService baseService;
    long id;
    private Context context;

    public IWannaLikeTask(Context context, long id) {
        this.context = context;
        baseService = new BaseService();
        baseService.setup(context);
        this.id = id;
    }

    public static void start(Context context, long id) {
        new IWannaLikeTask(context, id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Object doInBackground(Object[] params) {
        baseService.postData(String.format(Constants.getUrl(Constants.API_IWANNA_LIKE_URL), id), null, null, null, TAG, null);
        return null;
    }
}
