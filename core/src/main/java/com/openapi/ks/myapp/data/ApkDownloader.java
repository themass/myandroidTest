package com.openapi.ks.myapp.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.openapi.core.app.AppContext;
import com.openapi.network.http.HttpUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.R;
import com.openapi.core.config.Constants;

import java.io.File;

/** 下载新版 APK，进度显示在通知栏，下载完成后调起系统安装。 */
@SuppressWarnings("deprecation")
public final class ApkDownloader {

    private static final int NOTIFICATION_ID = 10021;
    private static final String D_CHANNEL = "D_CHANNEL";

    private ApkDownloader() {
    }

    public static void start(final Context context, final String url) {
        final File apkFile = new File(Environment.getExternalStorageDirectory(), Constants.TEMP_PATH + "/freevpn.apk");
        ToastUtil.showShort(R.string.about_download_begin);
        new Thread(new DownloadRunnable(context, url, apkFile)).start();
    }

    private static class DownloadRunnable implements Runnable, HttpUtils.DownloadListener {
        private final String url;
        private final File apkFile;
        private final Context context;
        private final Handler handler;
        private final NotificationCompat.Builder builder;
        private final NotificationManager notificationManager;

        private DownloadRunnable(Context context, String url, File apkFile) {
            this.context = context;
            this.url = url;
            this.apkFile = apkFile;
            this.handler = new Handler(Looper.getMainLooper());
            this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(D_CHANNEL, "FreeVPN", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("freevpn");
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                notificationManager.createNotificationChannel(channel);
            }
            builder = new NotificationCompat.Builder(AppContext.get(), D_CHANNEL);
        }

        @Override
        public void run() {
            try {
                showNotification(context.getString(R.string.about_download_title), context.getString(R.string.about_download_prepare));
                HttpUtils.download(context, url, apkFile, this);
                installFile();
            } catch (Exception e) {
                LogUtil.e(e);
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
            String percentText = String.format(context.getString(R.string.about_download_percent),
                    FileUtils.formatFileSize(current), FileUtils.formatFileSize(total));
            int percent = total == 0 ? 0 : (int) ((current * 1.0) * 100 / total);
            builder.setContentText(percentText);
            builder.setProgress(100, percent, false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        private void showNotification(String title, String message) {
            Intent notificationIntent = new Intent().setClassName(context, "com.openapi.home.HomeActivity");
            Bundle bundle = new Bundle();
            notificationIntent.putExtras(bundle);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
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
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
