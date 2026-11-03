package com.openapi.data.local;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.SplitTunnelingPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分应用代理策略的持久化与已安装应用枚举。
 * <p>
 * 存包名列表与模式字符串，不存枚举序号，便于将来扩展模式。
 */
public final class SplitTunnelingPreferences {

    private static final String TAG = "SplitTunnelPrefs";
    private static final String PREFS = "split_tunneling";
    private static final String KEY_MODE = "mode";
    private static final String KEY_PACKAGES = "packages";
    private static final String KEY_RECONNECT_PENDING = "reconnect_pending";

    private SplitTunnelingPreferences() {
    }

    /** 名单改过且需要重连才能生效时调用；首页下次显示时消费。 */
    public static void markReconnectPending(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_RECONNECT_PENDING, true).apply();
    }

    /** 读取并清除"待重连"标记。返回 true 表示上次修改后还没处理。 */
    public static boolean consumeReconnectPending(@NonNull Context context) {
        boolean pending = prefs(context).getBoolean(KEY_RECONNECT_PENDING, false);
        if (pending) {
            prefs(context).edit().putBoolean(KEY_RECONNECT_PENDING, false).apply();
        }
        return pending;
    }


    @NonNull
    public static SplitTunnelingPolicy getPolicy(@NonNull Context context) {
        android.content.SharedPreferences sp = prefs(context);
        String mode = sp.getString(KEY_MODE, SplitTunnelingPolicy.Mode.EXCLUDE.name());
        String raw = sp.getString(KEY_PACKAGES, "");
        Set<String> set = new LinkedHashSet<>();
        if (!raw.isEmpty()) {
            for (String p : raw.split(",")) {
                if (!p.trim().isEmpty()) {
                    set.add(p.trim());
                }
            }
        }
        SplitTunnelingPolicy.Mode m = "INCLUDE".equalsIgnoreCase(mode)
                ? SplitTunnelingPolicy.Mode.INCLUDE
                : SplitTunnelingPolicy.Mode.EXCLUDE;
        return SplitTunnelingPolicy.of(m, set);
    }

    public static void save(@NonNull Context context, @NonNull SplitTunnelingPolicy policy) {
        StringBuilder sb = new StringBuilder();
        for (String p : policy.getPackages()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        prefs(context).edit()
                .putString(KEY_MODE, policy.getMode().name())
                .putString(KEY_PACKAGES, sb.toString())
                .apply();
    }

    /**
     * 列出可参与分流的用户可见应用。
     * <p>
     * 排除本应用自身（总是需要走代理）以及未更新的系统组件。
     * 不再要求有桌面图标：很多需要分流的应用（后台服务类、部分游戏组件）没有启动入口。
     */
    @NonNull
    public static List<AppEntry> listInstalledApps(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        List<AppEntry> result = new ArrayList<>();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (Exception e) {
            LogUtil.e(TAG + ": 枚举应用失败: " + e.getMessage());
            return result;
        }
        String self = context.getPackageName();
        for (ApplicationInfo info : apps) {
            if (self.equals(info.packageName)) {
                continue;
            }
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system && !updatedSystem) {
                continue;
            }
            CharSequence label = pm.getApplicationLabel(info);
            result.add(new AppEntry(info.packageName,
                    label == null ? info.packageName : label.toString()));
        }
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    @NonNull
    private static android.content.SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 供 UI 展示的应用条目。 */
    public static final class AppEntry {
        public final String packageName;
        public final String label;

        public AppEntry(@NonNull String packageName, @NonNull String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }
}
