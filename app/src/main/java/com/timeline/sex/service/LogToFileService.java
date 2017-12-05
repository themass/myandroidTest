package com.timeline.sex.service;

import android.content.Intent;

import com.sspacee.common.log.WriteThread;

/**
 * Created by this on 2016/12/26.
 */
public class LogToFileService extends BaseLogService {
    WriteThread thread;

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new WriteThread();
        thread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        new LogTask(this,content).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (thread != null && thread.isAlive()) {
            thread.stopRun();
            thread = null;
        }
    }
}
