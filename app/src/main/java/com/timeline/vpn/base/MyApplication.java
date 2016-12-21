package com.timeline.vpn.base;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.sdk.android.feedback.impl.FeedbackAPI;
import com.alibaba.sdk.android.feedback.util.ErrorCode;
import com.alibaba.sdk.android.feedback.util.FeedbackErrorCallback;
import com.android.volley.VolleyLog;
import com.kyview.InitConfiguration;
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

import java.util.concurrent.Callable;

import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by themass on 2016/3/1.
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
        VolleyUtils.init(getApplicationContext());
        VolleyLog.DEBUG = SystemUtils.isApkDebugable(this);
        VolleyLog.setTag("VolleyUtils");
        initData();
        initFeedback();
        typeface = Typeface.SANS_SERIF;
        instance = this;
    }

    private void initData() {
        UserInfoVo user = PreferenceUtils.getPrefObj(this, Constants.LOGIN_USER, UserInfoVo.class);
        if (user != null) {
            StaticDataUtil.add(Constants.LOGIN_USER, user);
        }
        LogUtil.i("init data ok");
        MobAgent.init(this, SystemUtils.isApkDebugable(this));
        InitConfiguration.Builder builder = new InitConfiguration.Builder(this)
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
                .setInstlDisplayType(InitConfiguration.InstlDisplayType.DIALOG_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮

        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
        if (SystemUtils.isApkDebugable(this)) {
            builder.setRunMode(InitConfiguration.RunMode.TEST);
        }
        initConfig = builder.build();
    }
    private void initFeedback(){
        FeedbackAPI.addErrorCallback(new FeedbackErrorCallback() {
            @Override
            public void onError(Context context, String errorMessage, ErrorCode code) {
                Toast.makeText(context, "ErrMsg is: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
        // Feedback activity的回调
        FeedbackAPI.addLeaveCallback(new Callable() {
            @Override
            public Object call() throws Exception {
                Log.d("DemoApplication", "custom leave callback");
                return null;
            }
        });
        //建议放在此处做初始化
        FeedbackAPI.init(this, Constants.DEFAULT_FEEDBACK_APPKEY);
    }
}
