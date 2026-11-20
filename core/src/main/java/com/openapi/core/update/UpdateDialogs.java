package com.openapi.core.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.openapi.common.ui.dialog.AppDialogHost;
import com.openapi.common.ui.dialog.DialogAction;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.core.model.VersionVo;
import com.openapi.core.R;
import com.openapi.core.config.Constants;

/** 新版本提示框。 */
public final class UpdateDialogs {

    private UpdateDialogs() {
    }

    /**
     * 有新版本时的提示框。确定：打开应用商店的本应用页面。
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
                        openStorePage(context);
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
     * 去应用商店更新。
     *
     * 不再在应用内下载并安装 APK：Google Play 禁止应用用商店以外的方式自我更新（设备与网络滥用政策），
     * 而且在 targetSdk 24+ 用 file:// 安装 APK 会直接抛 FileUriExposedException。
     * 服务端下发的 url 只作为没有商店可用时的兜底（比如被浏览器打开的下载页）。
     */
    private static void openStorePage(Activity context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException noStore) {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.playStoreUrl(context.getPackageName())));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
        }
    }

}
