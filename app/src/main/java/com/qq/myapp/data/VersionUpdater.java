package com.qq.myapp.data;

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

import com.qq.common.util.DateUtils;
import com.qq.common.util.EventBusUtil;
import com.qq.common.util.FileUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PackageUtils;
import com.qq.common.util.PreferenceUtils;
import com.qq.common.util.StringUtils;
import com.qq.common.util.ToastUtil;
import com.qq.yewu.net.HttpDNSUtil;
import com.qq.yewu.net.HttpUtils;
import com.qq.yewu.net.NetUtils;
import com.qq.yewu.net.VolleyUtils;
import com.qq.yewu.net.request.CommonResponse;
import com.qq.yewu.net.request.GsonRequest;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.vo.VersionVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.config.StateUseEvent;
import com.qq.myapp.data.config.VipDescEvent;
import com.qq.ks.free1.R;
import com.qq.ks.ui.main.MainFragmentViewPage;

import java.io.File;
import java.util.Date;

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
                    if(vo!=null) {
                        VersionUpdater.setNewVersion(context, vo.maxBuild);
                        PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.D_URL, vo.url);
                        PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.ADS_SHOW_CONFIG, vo.adsShow);
                        PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.LOG_UPLOAD_CONFIG, vo.logUp);
                        PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.NEED_DNSPOD_CONFIG, vo.needDnspod);
                        PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.NEED_NATIVE_ADS_CONFIG, vo.needNative);
                        Constants.PROBABILITY = vo.probability==null?Constants.PROBABILITY:vo.probability;
                        if(StringUtils.hasText(vo.mobvistaNative)){
                            PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.Mob_UNIT_SPLASH_KAY, vo.mobvistaNative);
                        }
                        PreferenceUtils.setPrefFloat(MyApplication.getInstance(),Constants.TRAF_KEY,vo.traf);
                        if (StringUtils.hasText(vo.dnspodIp)) {
                            HttpDNSUtil.DNS_POD_IP = vo.dnspodIp;
                        }
                        if (vo.stateUse != null)
                            EventBusUtil.getEventBus().post(new StateUseEvent(vo.stateUse));
                        EventBusUtil.getEventBus().post(new VipDescEvent(vo.vipDesc));
                        if (VersionUpdater.isNewVersion(vo.maxBuild)
                                && StringUtils.hasText(vo.url) && vo.update) {
                            // 有新版本
                            VersionUpdater.showUpdateDialog(context, vo, true);
                            //                        VersionUpdater.showGoogleUpdateDialog(context, vo, true);
                        } else {
                            if (needToast)
                                ToastUtil.showShort(R.string.about_version_update_to_date);
                        }
                    }
                }
            }, new CommonResponse.ResponseErrorListener() {

            }, VERSION_TAG);
        }
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

    /**
     * 开始检查版本
     */
    public static void checkNewVersion(Context context, CommonResponse.ResponseOkListener listener, CommonResponse.ResponseErrorListener errorListener, String tag) {
        GsonRequest request = new GsonRequest(context, Constants.getUrl(Constants.API_VERSION_URL), VersionVo.class, null, listener, errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }

    /**
     * 显示版本更新提示框
     */

    public static void showGoogleUpdateDialog(final Activity context, final VersionVo vo, final boolean updatePrefIfCancel) {
        final String title = context.getString(R.string.about_version_download_title) + " V" + vo.version;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(vo.content);
        builder.setIcon(R.drawable.ic_small);
        if (vo.minBuild != vo.maxBuild) {
            builder.setNegativeButton(R.string.about_version_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (updatePrefIfCancel) {
                        // 保存pref配置
                        PreferenceUtils.setPrefBoolean(context, getPrefKey(vo.version, vo.maxBuild), false);
                    }
                }
            });
        }
        builder.setPositiveButton(R.string.about_version_download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if (NetUtils.isWifi(context) && PackageUtils.isApk(vo.url)) {
                    startDownloadThread(context, vo.url);
                } else {
                    final Uri uri = Uri.parse(vo.url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    public static void showUpdateDialog(final Activity context, final VersionVo vo, final boolean updatePrefIfCancel) {
        final String title = context.getString(R.string.about_version_download_title) + " V" + vo.version;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(vo.content);
        builder.setIcon(R.drawable.ic_small);
        if (vo.minBuild != vo.maxBuild) {
            builder.setNegativeButton(R.string.about_version_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (updatePrefIfCancel) {
                        // 保存pref配置
                        PreferenceUtils.setPrefBoolean(context, getPrefKey(vo.version, vo.maxBuild), false);
                    }
                }
            });
        }
        builder.setPositiveButton(R.string.about_version_download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (NetUtils.isWifi(context)) {
                    startDownloadThread(context, vo.url);
                } else {
                    // 非wifi情况下，提示用户是否下载
                    AlertDialog.Builder confirmDialog = new AlertDialog.Builder(context);
                    confirmDialog.setTitle(R.string.about_download_confirm_title);
                    confirmDialog.setMessage(R.string.about_download_confirm_content);
                    confirmDialog.setPositiveButton(R.string.about_version_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startDownloadThread(context, vo.url);
                            dialog.dismiss();
                        }
                    });
                    confirmDialog.setNegativeButton(R.string.about_version_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    confirmDialog.show();
                }
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private static String getPrefKey(String newVersion, int newVersionBuild) {
        return Constants.SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME + "_" + newVersion + "_" + newVersionBuild;
    }

    public static int getNewVersion(Context context) {
        return PreferenceUtils.getPrefInt(context, Constants.VERSION_APP_INCOMING, 0);
    }

    public static void setNewVersion(Context context, int build) {
        PreferenceUtils.setPrefInt(context, Constants.VERSION_APP_INCOMING, build);
    }

    private static void startDownloadThread(final Context context, final String url) {
        String data = DateUtils.format(new Date(),DateUtils.DATE_FORMAT);
        final File apkFile = new File(Environment.getExternalStorageDirectory(), Constants.TEMP_PATH + "/freevpn-"+data+".apk");
        ToastUtil.showShort(R.string.about_download_begin);
        new Thread(new DownloadRunnable(context, url, apkFile)).start();
    }


    private static class DownloadRunnable implements Runnable, HttpUtils.DownloadListener {
        private final String url;
        private final File apkFile;
        private Context context;
        private Handler handler;
        private NotificationCompat.Builder  builder;
        private NotificationManager notificationManager;

        private DownloadRunnable(Context context, String url, File apkFile) {
            this.context = context;
            this.url = url;
            this.apkFile = apkFile;
            this.handler = new Handler();
            this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(D_CHANNEL, "FreeVPN", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("freevpn");
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                notificationManager.createNotificationChannel(channel);
            }
            builder = new NotificationCompat.Builder(MyApplication.getInstance(),D_CHANNEL);
        }

        @Override
        public void run() {
            try {
                showNotification(context.getString(R.string.about_download_title), context.getString(R.string.about_download_prepare));
                HttpUtils.download(context, url, apkFile, this);
                installFile();
            } catch (Exception e) {
                e.printStackTrace();
                showNotification(context.getString(R.string.about_download_title), context.getString(R.string.about_download_error));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.showShort(R.string.about_download_error);
                    }
                });
            }
        }

        @Override
        public void onDownloading(long current, long total) {
            String percentText = String.format(context.getString(R.string.about_download_percent), FileUtils.formatFileSize(current), FileUtils.formatFileSize(total));
            int percent = total == 0 ? 0 : (int) ((current * 1.0) * 100 / total);
            builder.setContentText(percentText);
            builder.setProgress(100, percent, false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        private void showNotification(String title, String message) {
            // 创建一个NotificationManager的引用

            Intent notificationIntent = new Intent(context, MainFragmentViewPage.class); //点击该通知后要跳转的Activity
            Bundle bundle = new Bundle();
            notificationIntent.putExtras(bundle);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            // 定义Notification的各种属性
            Notification notification = builder.setContentTitle(title).setContentText(message)
                    .setSmallIcon(R.drawable.ic_launcher).setContentIntent(contentIntent)
                    .build();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notificationManager.notify(NOTIFICATION_ID, notification);
        }

        private void installFile() {
            hideNotification();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    ToastUtil.showShort(R.string.about_download_finish);
                }
            });

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        private void hideNotification() {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
