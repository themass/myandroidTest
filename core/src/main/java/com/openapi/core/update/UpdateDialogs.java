package com.openapi.core.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.openapi.common.ui.dialog.AppDialogHost;
import com.openapi.common.ui.dialog.DialogAction;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.net.http.NetUtils;
import com.openapi.core.model.VersionVo;
import com.openapi.core.R;
import com.openapi.core.config.Constants;

/** 新版本提示框。 */
public final class UpdateDialogs {

    private UpdateDialogs() {
    }

    /**
     * 有新版本时的提示框。确定：wifi 且是 apk 直接下载，否则用浏览器打开下载地址。
     * 只有当 minBuild 与 maxBuild 不同（允许不更新）时才有取消按钮；取消时记下偏好。
     */
    public static void showGoogleUpdate(final Activity context, final VersionVo vo, final boolean updatePrefIfCancel) {
        final String title = context.getString(R.string.about_version_download_title) + " V" + vo.version;
        final boolean hasCancel = vo.minBuild != vo.maxBuild;
        AppDialogHost.showMessage(context, title, vo.content, R.drawable.ic_launcher,
                context.getString(R.string.about_version_download),
                new DialogAction() {
                    @Override
                    public void perform() {
                        if (NetUtils.isWifi(context) && PackageUtils.isApk(vo.url)) {
                            startDownloadWithNotificationPermission(context, vo.url);
                        } else {
                            final Uri uri = Uri.parse(vo.url);
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    }
                },
                hasCancel ? context.getString(R.string.about_version_cancel) : null,
                hasCancel ? new DialogAction() {
                    @Override
                    public void perform() {
                        if (updatePrefIfCancel) {
                            PreferenceUtils.setPrefBoolean(context, VersionUpdater.updateReminderKey(vo.version, vo.maxBuild), false);
                        }
                    }
                } : null,
                false);
    }

    /**
     * 下载前先申请通知权限（Android 13 起是运行时权限）。
     *
     * 没有它，下载进度通知会被系统静默丢弃：用户点了下载，界面什么反应都没有，
     * 只能干等到装包页弹出来。申请被拒也照样下载，只是没有进度通知。
     */
    private static void startDownloadWithNotificationPermission(final Activity context, final String url) {
        final com.openapi.common.ui.permission.PermissionRequest request =
                com.openapi.common.ui.permission.Permissions.NOTIFICATIONS;
        // 低版本这一组是空的：系统本来就没有这个权限，直接下载
        if (request.getPermissions().isEmpty()) {
            ApkDownloader.start(context, url);
            return;
        }
        // 统一权限入口只支持 ComponentActivity；更新提示可能来自老式 Activity，
        // 那种情况下没有 Activity Result API，直接下载（只是没有进度通知）
        if (!(context instanceof androidx.activity.ComponentActivity)) {
            ApkDownloader.start(context, url);
            return;
        }
        com.openapi.common.ui.permission.PermissionRequester.with((androidx.activity.ComponentActivity) context)
                .permissions(request.getPermissions().toArray(new String[0]))
                .rationale(request.getRationale())
                .request(new com.openapi.common.ui.permission.PermissionRequester.Callback() {
                    @Override
                    public void onGranted() {
                        ApkDownloader.start(context, url);
                    }

                    @Override
                    public void onDenied(java.util.List<String> denied) {
                        // 拒绝通知权限不影响下载本身
                        ApkDownloader.start(context, url);
                    }
                });
    }

}
