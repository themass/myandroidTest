package com.timeline.vpn.base;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;

import com.android.volley.VolleyLog;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.VersionUpdater;

import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by gqli on 2016/3/1.
 */
public class MyApplication extends Application {
    private RefWatcher refWatcher;
    public Typeface typeface;
    private static MyApplication instance = null;
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
        VersionUpdater.init(this);
        VolleyLog.DEBUG = Constants.IS_DEBUG_OPEN;
        VolleyLog.setTag("VolleyUtils");
        VolleyUtils.init(getApplicationContext());
        SystemUtils.copyDB(this);// 程序第一次运行将数据库copy过去
        initData();
        typeface = Typeface.SANS_SERIF;
        instance = this;
    }
    public static MyApplication getInstance(){
        return instance;
    }
    private void initData(){
        UserInfoVo user = PreferenceUtils.getPrefObj(this, Constants.LOGIN_USER, UserInfoVo.class);
        if(user!=null) {
            StaticDataUtil.add(Constants.LOGIN_USER, user);
        }
        MobAgent.init(SystemUtils.isApkDebugable(this));
    }
}
