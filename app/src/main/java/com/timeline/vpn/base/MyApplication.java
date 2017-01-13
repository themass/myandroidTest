package com.timeline.vpn.base;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.multidex.MultiDexApplication;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.sdk.android.feedback.impl.FeedbackAPI;
import com.alibaba.sdk.android.feedback.util.ErrorCode;
import com.alibaba.sdk.android.feedback.util.FeedbackErrorCallback;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.util.DeviceInfoUtils;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.service.LogUploadService;
import com.umeng.message.IUmengRegisterCallback;
import com.umeng.message.MsgConstant;
import com.umeng.message.PushAgent;

import java.util.concurrent.Callable;

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
        typeface = Typeface.SANS_SERIF;
        instance = this;
        long start = System.currentTimeMillis();
//        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(isDebug);
        VersionUpdater.init(this);
        VolleyUtils.init();
        initFilePath();
        initFeedback();
        initPush();
        if (MyApplication.isDebug) {
            String uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
            String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
            LogUtil.i("uc=" + uc + "; ad=" + ad);
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.e("app start cost:" + cost);
    }

    private void initFilePath() {
        tmpFilePath = FileUtils.getWriteFilePath(this, Constants.FILE_TMP_PATH);
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);
        startService(new Intent(this, LogUploadService.class));
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
