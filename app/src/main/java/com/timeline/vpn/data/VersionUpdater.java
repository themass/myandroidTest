package com.timeline.vpn.data;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.VersionVo;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.NetUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.net.request.GsonRequest;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.main.MainFragment;

import java.io.File;

public class VersionUpdater {
    private static int NOTIFICATION_ID = 10021;
    private static String version;
    private static int build;
    private static boolean isInitVersionSuccess = false;

    /**
     * 初始化版本信息
     */
    public static void init(Context context) {
        // 取得版本号
        PackageManager manager = context.getPackageManager();
        PackageInfo info = null;
        try {
            info = manager.getPackageInfo(context.getPackageName(), 0);
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
    public static void checkNewVersion(Context context,CommonResponse.ResponseOkListener listener, CommonResponse.ResponseErrorListener errorListener, String tag) {
        GsonRequest request = new GsonRequest(context, Constants.VERSION_URL, VersionVo.class,null,listener,errorListener);
        request.setTag(tag);
        VolleyUtils.addRequest(request);
    }
    /**
     * 显示版本更新提示框
     */
    public static void showUpdateDialog(final Activity context, String content, final String url, final String newVersion,
                                        final int newVersionBuild, final boolean updatePrefIfCancel) {
        final String title = context.getString(R.string.about_version_download_title) + " V" + newVersion;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setNegativeButton(R.string.about_version_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (updatePrefIfCancel) {
                    // 保存pref配置
                    PreferenceUtils.setPrefBoolean(context, getPrefKey(newVersion, newVersionBuild), false);
                }
            }
        });
        builder.setPositiveButton(R.string.about_version_download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (NetUtils.isWifi(context)) {
                    startDownloadThread(context, url);
                } else {
                    // 非wifi情况下，提示用户是否下载
                    AlertDialog.Builder confirmDialog = new AlertDialog.Builder(context);
                    confirmDialog.setTitle(R.string.about_download_confirm_title);
                    confirmDialog.setMessage(R.string.about_download_confirm_content);
                    confirmDialog.setPositiveButton(R.string.about_version_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startDownloadThread(context, url);
                            dialog.dismiss();
                        }
                    })
                            .setNegativeButton(R.string.about_version_cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).show();
                }
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private static String getPrefKey(String newVersion, int newVersionBuild) {
        return Constants.SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME + "_" + newVersion + "_" + newVersionBuild;
    }
    public static int getNewVersion(Context context){
        return PreferenceUtils.getPrefInt(context, Constants.VERSION_APP_INCOMING, 0);
    }
    public static void setNewVersion(Context context,int build){
        PreferenceUtils.setPrefInt(context, Constants.VERSION_APP_INCOMING, build);
    }
    private static void startDownloadThread(final Context context, final String url) {
        final File apkFile = new File(Environment.getExternalStorageDirectory(), Constants.TEMP_PATH + "/freevpn.apk");
        Toast.makeText(context, R.string.about_download_begin, Toast.LENGTH_SHORT).show();
        new Thread(new DownloadRunnable(context, url, apkFile)).start();
    }


    private static class DownloadRunnable implements Runnable, HttpUtils.DownloadListener {
        private Context context;
        private final String url;
        private final File apkFile;
        private Handler handler;
        private Notification.Builder builder;
        private NotificationManager notificationManager;

        private DownloadRunnable(Context context, String url, File apkFile) {
            this.context = context;
            this.url = url;
            this.apkFile = apkFile;
            this.handler = new Handler();
            this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            this.builder = new Notification.Builder(context);
        }

        @Override
        public void run() {
            try {
                showNotification(context.getString(R.string.about_download_title), context.getString(R.string.about_download_prepare));
                HttpUtils.download(context,url, apkFile, this);
                installFile();
            } catch (Exception e) {
                e.printStackTrace();
                showNotification(context.getString(R.string.about_download_title), context.getString(R.string.about_download_error));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.about_download_error, Toast.LENGTH_SHORT).show();
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
            notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
        }

        private void showNotification(String title, String message) {
            // 创建一个NotificationManager的引用
            Intent notificationIntent = new Intent(context, MainFragment.class); //点击该通知后要跳转的Activity
            Bundle bundle = new Bundle();
            notificationIntent.putExtras(bundle);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            // 定义Notification的各种属性
            Notification notification = builder.setContentTitle(title).setContentText(message)
                    .setSmallIcon(R.drawable.ic_launcher).setContentIntent(contentIntent)
                    .getNotification();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notificationManager.notify(NOTIFICATION_ID, notification);
        }

        private void installFile() {
            hideNotification();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, R.string.about_download_finish, Toast.LENGTH_SHORT).show();
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
