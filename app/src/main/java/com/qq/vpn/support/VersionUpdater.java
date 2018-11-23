package com.qq.vpn.support;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;

import com.qq.Constants;
import com.qq.MyApplication;
import com.qq.ext.network.HttpUtils;
import com.qq.ext.network.NetUtils;
import com.qq.ext.network.VolleyUtils;
import com.qq.ext.network.req.CommonResponse;
import com.qq.ext.network.req.GsonRequest;
import com.qq.ext.util.EventBusUtil;
import com.qq.ext.util.FileUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PackageUtils;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.StringUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.network.R;
import com.qq.vpn.domain.res.VersionVo;
import com.qq.vpn.support.config.StateUseEvent;
import com.qq.vpn.support.config.VipDescEvent;

import java.io.File;

public class VersionUpdater {
    private static final String VERSION_TAG = "REQUEST_VERSION_CHECK";
    private static int NOTIFICATION_ID = 10021;
    private static String version;
    private static int build;
    private static boolean isInitVersionSuccess = false;
    private static final String D_CHANNEL= "D_CHANNEL";

    public static void checkUpdate(final Activity context, final boolean needToast) {
        // 检查版本更新
        if (VersionUpdater.isInitVersionSuccess()) {
            VersionUpdater.checkNewVersion(context, new CommonResponse.ResponseOkListener<VersionVo>() {
                @Override
                public void onResponse(final VersionVo vo) {
                    VersionUpdater.setNewVersion(context, vo.maxBuild);
                    PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.D_URL, vo.url);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.ADS_SHOW_CONFIG, vo.adsShow);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.LOG_UPLOAD_CONFIG, vo.logUp);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.NEED_DNSPOD_CONFIG, vo.needDnspod);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.NEED_NATIVE_ADS_CONFIG, vo.needNative);
                    if (vo.stateUse != null)
                        EventBusUtil.getEventBus().post(new StateUseEvent(vo.stateUse));
                    EventBusUtil.getEventBus().post(new VipDescEvent(vo.vipDesc));
                    if (VersionUpdater.isNewVersion(vo.maxBuild)
                            && StringUtils.hasText(vo.url)) {
                        // 有新版本
//                        VersionUpdater.showUpdateDialog(context, vo, true);
//                        VersionUpdater.showGoogleUpdateDialog(context, vo, true);
                    } else {
//                        if (needToast)
//                            ToastUtil.showShort(R.string.about_version_update_to_date);
                    }
                }
            }, new CommonResponse.ResponseErrorListener() {

            }, VERSION_TAG);
        }
    }
    public static void setNewVersion(Context context, int build) {
        PreferenceUtils.setPrefInt(context, Constants.VERSION_APP_INCOMING, build);
    }
    /**
     * 初始化版本信息
     */
    public static void init(Context context) {
        // 取得版本号
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
            build = info.versionCode;
            isInitVersionSuccess = true;
        } catch (Exception e) {
            LogUtil.e(e);
        }
    }

    /**
     * 是否初始化成功，防止读取到错误数据
     */
    public static boolean isInitVersionSuccess() {
        return isInitVersionSuccess;
    }

    /**
     * 启动app是否还需要提示版本更新
     */
    public static boolean needToCheckUpdateNextTime(Context context, String newVersion, int newVersionBuild) {
        return PreferenceUtils.getPrefBoolean(context, getPrefKey(newVersion, newVersionBuild), true);
    }

    /**
     * 是否是新版本
     */
    public static boolean isNewVersion(int newVersionBuild) {
        return newVersionBuild > build;
    }

    public static String getVersion() {
        return version;
    }

    public static int getBuild() {
        return build;
    }
    private static String getPrefKey(String newVersion, int newVersionBuild) {
        return Constants.SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME + "_" + newVersion + "_" + newVersionBuild;
    }
    /**
     * 开始检查版本
     */
    public static void checkNewVersion(Context context, CommonResponse.ResponseOkListener listener, CommonResponse.ResponseErrorListener errorListener, String tag) {
        GsonRequest request = new GsonRequest(context, Constants.getUrl(Constants.API_VERSION_URL), VersionVo.class, null, listener, errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

}
