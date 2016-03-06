package com.timeline.vpn.base;

import android.app.Application;
import android.content.Context;

import com.android.volley.VolleyLog;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.constant.Constants;

import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by gqli on 2016/3/1.
 */
public class MyApplication extends Application {
    private RefWatcher refWatcher;

    public static RefWatcher getRefWatcher(Context context) {
        MyApplication application = (MyApplication) context.getApplicationContext();
        return application.refWatcher;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(false);
        VolleyLog.DEBUG = Constants.IS_DEBUG_OPEN;
        VolleyLog.setTag("VolleyUtils");
        VolleyUtils.init(getApplicationContext());
    }
}
