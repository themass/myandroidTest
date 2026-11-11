package com.openapi.ks.myapp.data;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.openapi.common.design.dialog.AppDialogHost;
import com.openapi.common.design.dialog.DialogAction;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.net.NetUtils;
import com.openapi.ks.myapp.bean.vo.VersionVo;
import com.openapi.core.R;
import com.openapi.ks.myapp.constant.Constants;

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
                            ApkDownloader.start(context, vo.url);
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
                            PreferenceUtils.setPrefBoolean(context, getPrefKey(vo.version, vo.maxBuild), false);
                        }
                    }
                } : null,
                false);
    }

    private static String getPrefKey(String newVersion, int newVersionBuild) {
        return Constants.SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME + "_" + newVersion + "_" + newVersionBuild;
    }
}
