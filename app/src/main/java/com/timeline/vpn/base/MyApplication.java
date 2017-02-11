package com.timeline.vpn.base;

import android.content.Intent;
import android.graphics.Typeface;
import android.support.multidex.MultiDexApplication;

import com.sspacee.common.net.VolleyUtils;
import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.DeviceInfoUtils;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.VersionUpdater;
import com.umeng.message.IUmengRegisterCallback;
import com.umeng.message.MsgConstant;
import com.umeng.message.PushAgent;

import butterknife.ButterKnife;

/**
 * Created by themass on 2016/3/1.
 */
public class MyApplication extends MultiDexApplication {
    //    private RefWatcher refWatcher;
    public static final String UPDATE_STATUS_ACTION = "com.timeline.vpn.action.UPDATE_STATUS";
    public static String tmpFilePath = "";
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    public Typeface typeface;

    //    public static RefWatcher getRefWatcher(Context context) {
//        MyApplication application = (MyApplication) context.getApplicationContext();
//        return application.refWatcher;
//    }
    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isDebug = SystemUtils.isApkDebugable(this);
        LogUtil.e("isDebug="+isDebug);
        typeface = Typeface.SANS_SERIF;
        instance = this;
        long start = System.currentTimeMillis();
//        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(isDebug);
        VersionUpdater.init(this);
        VolleyUtils.init();
        initFilePath();
        initPush();
        if (MyApplication.isDebug) {
            String uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
            String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
            LogUtil.i("uc=" + uc + "; ad=" + ad);
            DensityUtil.logDensity(this);
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.e("app start cost:" + cost);
    }

    private void initFilePath() {
        tmpFilePath = FileUtils.getWriteFilePath(this, Constants.FILE_TMP_PATH);
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);
    }
    private void initPush() {
        //友盟统计
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

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
