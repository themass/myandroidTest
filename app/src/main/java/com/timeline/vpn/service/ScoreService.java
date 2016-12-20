package com.timeline.vpn.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by themass on 2016/8/29.
 */
public class ScoreService extends Service {
    LocalBinder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        public ScoreService getService() {
            return ScoreService.this;
        }
    }
}
