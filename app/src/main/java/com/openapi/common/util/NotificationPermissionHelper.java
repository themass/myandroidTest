package com.openapi.common.util;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * VPN 状态通知的权限处理，按系统版本分支：
 * <ul>
 *   <li>Android 13（API 33）以下：没有运行时通知权限，只看系统里 App 的通知开关；</li>
 *   <li>Android 13+：需要运行时授权 POST_NOTIFICATIONS；拒绝过且系统不再弹窗时，只能引导去设置。</li>
 * </ul>
 * 调用时机：用户第一次点连接（最有动机授权），不在启动时弹。
 */
public final class NotificationPermissionHelper {

    public static final int REQUEST_CODE = 2001;

    private NotificationPermissionHelper() {
    }

    /** 通知当前是否可用（运行时权限 + 系统整体开关都要通过）。 */
    public static boolean canPost(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(activity).areNotificationsEnabled();
    }

    /**
     * 不可用时尝试请求。返回 true 表示已发出请求（等待回调，此次连接先不进行），
     * 返回 false 表示无法通过弹窗解决（已引导去设置或不需要处理）。
     */
    public static boolean requestIfNeeded(Activity activity) {
        if (canPost(activity)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                    || !wasAskedBefore(activity)) {
                markAsked(activity);
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
                return true;
            }
        }
        openAppNotificationSettings(activity);
        return false;
    }

    public static void openAppNotificationSettings(Activity activity) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        if (i.resolveActivity(activity.getPackageManager()) == null) {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
        }
        activity.startActivity(i);
    }

    private static final String PREF = "notif_perm";
    private static final String KEY_ASKED = "asked";

    private static boolean wasAskedBefore(Activity a) {
        return a.getSharedPreferences(PREF, Activity.MODE_PRIVATE).getBoolean(KEY_ASKED, false);
    }

    private static void markAsked(Activity a) {
        a.getSharedPreferences(PREF, Activity.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply();
    }
}
