package com.timeline.vpn.base;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;

import com.android.volley.VolleyLog;
import com.kyview.InitConfiguration;
import com.kyview.manager.AdViewBannerManager;
import com.kyview.manager.AdViewInstlManager;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.manager.AdViewSpreadManager;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.ui.feedback.ConversationDetailActivity;
import com.umeng.fb.push.FeedbackPush;

import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by gqli on 2016/3/1.
 */
public class MyApplication extends Application {
    private static MyApplication instance = null;
    public Typeface typeface;
    private RefWatcher refWatcher;
    private InitConfiguration initConfig;

    public static RefWatcher getRefWatcher(Context context) {
        MyApplication application = (MyApplication) context.getApplicationContext();
        return application.refWatcher;
    }

    public static InitConfiguration getInitConfig(Context context) {
        MyApplication application = (MyApplication) context.getApplicationContext();
        return application.initConfig;
    }

    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(SystemUtils.isApkDebugable(this));
        VersionUpdater.init(this);
        VolleyLog.DEBUG = SystemUtils.isApkDebugable(this);
        VolleyLog.setTag("VolleyUtils");
        VolleyUtils.init(getApplicationContext());
        SystemUtils.copyDB(this);// 程序第一次运行将数据库copy过去
        initData();
        typeface = Typeface.SANS_SERIF;
        instance = this;
    }

    private void initData() {
        UserInfoVo user = PreferenceUtils.getPrefObj(this, Constants.LOGIN_USER, UserInfoVo.class);
        if (user != null) {
            StaticDataUtil.add(Constants.LOGIN_USER, user);
        }
        LogUtil.i("init data ok");
        MobAgent.init(SystemUtils.isApkDebugable(this));
        com.umeng.fb.util.Log.LOG = SystemUtils.isApkDebugable(this);
        FeedbackPush.getInstance(this).init(ConversationDetailActivity.class, true);
        InitConfiguration.Builder builder = new InitConfiguration.Builder(this)
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.CANCLOSED)    //横幅可关闭按钮
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮
        if (SystemUtils.isApkDebugable(this)) {
            builder.setRunMode(InitConfiguration.RunMode.TEST);
        }
        //测试模式，log更多，应用上线后可删
        initConfig = builder.build();
        AdViewBannerManager.getInstance(this).init(initConfig, Constants.adsKeySet);
        AdViewInstlManager.getInstance(this).init(initConfig, Constants.adsKeySet);
        AdViewNativeManager.getInstance(this).init(initConfig, Constants.adsKeySet);
        AdViewSpreadManager.getInstance(this).init(initConfig, Constants.adsKeySet);

    }
}
