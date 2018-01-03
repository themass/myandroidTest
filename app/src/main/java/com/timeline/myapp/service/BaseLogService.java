package com.timeline.myapp.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.sspacee.common.util.LogUtil;

/**
 * Created by themass on 2016/12/27.
 */
public class BaseLogService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onBind");
        return null;
    }

    @Override
    public void onCreate() {
        LogUtil.i(getClass().getSimpleName() + "-onCreate");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        LogUtil.i(getClass().getSimpleName() + "-onDestroy");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.i(getClass().getSimpleName() + "-onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onLowMemory() {
        LogUtil.i(getClass().getSimpleName() + "-onLowMemory");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        LogUtil.i(getClass().getSimpleName() + "-onTrimMemory");
        super.onTrimMemory(level);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onRebind");
        super.onRebind(intent);
    }
}
