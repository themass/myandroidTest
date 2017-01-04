package com.timeline.vpn.base;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
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
import com.timeline.vpn.common.util.DeviceInfoUtils;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.service.LogUploadService;
import com.umeng.message.IUmengRegisterCallback;
import com.umeng.message.MsgConstant;
import com.umeng.message.PushAgent;

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
    public static final String UPDATE_STATUS_ACTION = "com.timeline.vpn.action.UPDATE_STATUS";
    public static String tmpFilePath = "";
    public static volatile boolean isDebug = true;
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
        isDebug = SystemUtils.isApkDebugable(this);
        long start = System.currentTimeMillis();
        Timber.plant(new Timber.DebugTree());
        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(isDebug);
        VersionUpdater.init(this);
        VolleyUtils.init(getApplicationContext());
        VolleyLog.DEBUG = isDebug;
        VolleyLog.setTag("VolleyUtils");
        initFilePath();
        initData();
        initFeedback();
        initPush();
        typeface = Typeface.SANS_SERIF;
        instance = this;
        long cost = System.currentTimeMillis()-start;
        if(MyApplication.isDebug) {
            String uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
            String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
            LogUtil.i("uc=" + uc + "; ad=" + ad);
        }
        LogUtil.e("app start cost:"+cost);
    }

    private void initFilePath() {
        tmpFilePath = FileUtils.getWriteFilePath(this, Constants.FILE_TMP_PATH);
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);
        startService(new Intent(this,LogUploadService.class));
    }

    private void initData() {
        UserInfoVo user = PreferenceUtils.getPrefObj(this, Constants.LOGIN_USER, UserInfoVo.class);
        if (user != null) {
            StaticDataUtil.add(Constants.LOGIN_USER, user);
        }
        LogUtil.i("init data ok");
        MobAgent.init(this, isDebug);
        InitConfiguration.Builder builder = new InitConfiguration.Builder(this)
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
                .setInstlDisplayType(InitConfiguration.InstlDisplayType.DIALOG_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮

        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
        if (isDebug) {
            builder.setRunMode(InitConfiguration.RunMode.TEST);
        }else{
            builder.setRunMode(InitConfiguration.RunMode.NORMAL);
        }
        initConfig = builder.build();
    }

    private void initFeedback() {
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
    }

    private void initPush() {
        //友盟统计
//        String channelId = DeviceInfoUtils.getChannelFromApk(this, "vpn");
//        String key = DeviceInfoUtils.getMetaData(this, "UMENG_APPKEY");
//        LogUtil.i("channelId=" + channelId);
//        LogUtil.i("key=" + key);
//        MobclickAgent.UMAnalyticsConfig config = new MobclickAgent.UMAnalyticsConfig(this, key, channelId);
//        MobclickAgent.startWithConfigure(config);
        //友盟推送
        PushAgent mPushAgent = PushAgent.getInstance(this);
//        mPushAgent.setMessageChannel(channelId);
        mPushAgent.setDebugMode(isDebug);
        //sdk开启通知声音
        mPushAgent.setNotificationPlaySound(MsgConstant.NOTIFICATION_PLAY_SERVER);
        mPushAgent.setNotificationPlayLights(MsgConstant.NOTIFICATION_PLAY_SDK_DISABLE);
        mPushAgent.setNotificationPlayVibrate(MsgConstant.NOTIFICATION_PLAY_SDK_DISABLE);
        mPushAgent.register(new IUmengRegisterCallback() {
            @Override
            public void onSuccess(String deviceToken) {
                LogUtil.i("device token: " + deviceToken);
                sendBroadcast(new Intent(UPDATE_STATUS_ACTION));
            }

            @Override
            public void onFailure(String s, String s1) {
                LogUtil.i("register failed: " + s + " " + s1);
                sendBroadcast(new Intent(UPDATE_STATUS_ACTION));
            }
        });
    }
}
